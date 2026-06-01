package com.licitacao.municipal.controller;

import com.licitacao.municipal.dto.DefesaResponse;
import com.licitacao.municipal.dto.RefinamentoRequest;
import com.licitacao.municipal.model.Analise;
import com.licitacao.municipal.model.MensagemConversa;
import com.licitacao.municipal.service.AnaliseService;
import com.licitacao.municipal.service.PdfGeradorService;
import com.licitacao.municipal.service.TceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class AnaliseController {

    private final PdfGeradorService pdfGeradorService;
    private final AnaliseService analiseService;
    private final TceService tceService;

    // =========================================================================
    // POST /api/defesa — Upload inicial do PDF do cliente
    // Retorna JSON (não mais PDF direto) para permitir o chat de refinamento
    // =========================================================================

    /**
     * Recebe o PDF da declaração do município, executa o fluxo RAG completo
     * e retorna o resultado como JSON para que o front-end possa exibir
     * o texto e iniciar a sessão de refinamento interativo.
     */
    @PostMapping(value = "/defesa", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DefesaResponse> criarDefesa(
            @RequestParam("declaracaoCliente") MultipartFile declaracaoCliente) {
        try {
            log.info("POST /api/defesa — arquivo: {}", declaracaoCliente.getOriginalFilename());
            Analise analise = analiseService.analisar(declaracaoCliente);
            return ResponseEntity.ok(toResponse(analise));
        } catch (IOException e) {
            log.error("Erro ao processar arquivo", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // =========================================================================
    // POST /api/defesa/{id}/chat — Refinamento interativo (o "estagiário sênior")
    // =========================================================================

    /**
     * Recebe uma instrução do advogado e atualiza a defesa de acordo.
     *
     * Exemplos de body:
     *   { "instrucao": "O município errou, mas de boa-fé. Busque argumentos para atenuar a multa." }
     *   { "instrucao": "O TCE só pediu um documento. Faça apenas um ofício padrão de encaminhamento." }
     *   { "instrucao": "Aprofunde a pesquisa nas decisões do TCU sobre dispensa de licitação." }
     */
    @PostMapping("/defesa/{id}/chat")
    public ResponseEntity<DefesaResponse> chat(
            @PathVariable Long id,
            @RequestBody RefinamentoRequest request) {
        try {
            log.info("POST /api/defesa/{}/chat — instrucao: {}", id, request.getInstrucao());

            if (request.getInstrucao() == null || request.getInstrucao().isBlank()) {
                return ResponseEntity.badRequest().build();
            }

            Analise analise = analiseService.processarMensagem(id, request.getInstrucao());
            return ResponseEntity.ok(toResponse(analise));

        } catch (IllegalArgumentException e) {
            log.warn("Análise não encontrada: {}", id);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Erro ao processar refinamento", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // =========================================================================
    // GET /api/defesa/{id}/pdf — Gera o PDF final a qualquer momento
    // =========================================================================

    /**
     * Gera e retorna o PDF da versão atual da defesa.
     * Pode ser chamado a qualquer momento — antes ou depois de refinamentos.
     */
    @GetMapping(value = "/defesa/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> gerarPdf(@PathVariable Long id) {
        try {
            Analise analise = analiseService.buscarPorId(id);
            if (analise == null) return ResponseEntity.notFound().build();

            byte[] pdfBytes = pdfGeradorService.gerarPdfDefesa(
                    analise.getNomeArquivo(),
                    analise.getResultadoDefesa(),
                    analise.getPdfTceUtilizado()
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment",
                    "defesa_" + analise.getNomeArquivo());
            headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");

            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);

        } catch (Exception e) {
            log.error("Erro ao gerar PDF para análise {}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // =========================================================================
    // GET /api/defesa/{id} — Busca o estado atual de uma análise
    // =========================================================================

    @GetMapping("/defesa/{id}")
    public ResponseEntity<DefesaResponse> buscarAnalise(@PathVariable Long id) {
        Analise analise = analiseService.buscarPorId(id);
        if (analise == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(toResponse(analise));
    }

    // =========================================================================
    // GET /api/historico — Lista todos os casos
    // =========================================================================

    @GetMapping("/historico")
    public ResponseEntity<List<Map<String, Object>>> historico() {
        List<Map<String, Object>> resumos = analiseService.listarHistorico().stream()
                .map(a -> Map.<String, Object>of(
                        "id", a.getId(),
                        "nomeArquivo", a.getNomeArquivo() != null ? a.getNomeArquivo() : "",
                        "dataAnalise", a.getDataAnalise() != null ? a.getDataAnalise().toString() : "",
                        "pdfTceUtilizado", a.getPdfTceUtilizado() != null ? a.getPdfTceUtilizado() : ""
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(resumos);
    }

    // =========================================================================
    // POST /api/tce/baixar — Download manual do diário
    // =========================================================================

    @PostMapping("/tce/baixar")
    public ResponseEntity<?> baixarTce() {
        try {
            tceService.baixarDiarioDoDia();
            return ResponseEntity.ok(Map.of(
                    "mensagem", "Processamento do Diário Oficial disparado com sucesso!",
                    "status", "Confira os logs do console para acompanhar o fatiamento"
            ));
        } catch (Exception e) {
            log.error("Erro ao forçar download manual", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("erro", "Falha ao processar: " + e.getMessage()));
        }
    }

    // =========================================================================
    // GET /api/health
    // =========================================================================

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "servico", "Licitacao Municipal v2 — Chat Ativo"));
    }

    // =========================================================================
    // HELPER
    // =========================================================================

    private DefesaResponse toResponse(Analise analise) {
        DefesaResponse resp = new DefesaResponse();
        resp.setId(analise.getId());
        resp.setNomeArquivo(analise.getNomeArquivo());
        resp.setDataAnalise(analise.getDataAnalise());
        resp.setResultadoDefesa(analise.getResultadoDefesa());
        resp.setPdfTceUtilizado(analise.getPdfTceUtilizado());

        // Deserializa os metadados do RAG de volta para listas
        resp.setLeisUtilizadas(splitPipe(analise.getLeisUtilizadas()));
        resp.setDiariosUtilizados(splitPipe(analise.getDiariosUtilizados()));

        List<MensagemConversa> msgs = analiseService.buscarConversa(analise.getId());
        resp.setConversa(msgs.stream()
                .map(DefesaResponse::toMensagemDto)
                .collect(Collectors.toList()));
        return resp;
    }

    /** Divide uma string pipe-separated em lista, retornando lista vazia se null/blank. */
    private List<String> splitPipe(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.asList(value.split("\\|"));
    }
}
