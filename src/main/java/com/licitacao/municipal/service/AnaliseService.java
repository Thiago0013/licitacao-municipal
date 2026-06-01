package com.licitacao.municipal.service;

import com.licitacao.municipal.model.Analise;
import com.licitacao.municipal.model.DiarioChunk;
import com.licitacao.municipal.model.Lei;
import com.licitacao.municipal.model.MensagemConversa;
import com.licitacao.municipal.repository.AnaliseRepository;
import com.licitacao.municipal.repository.DiarioChunkRepository;
import com.licitacao.municipal.repository.LeiRepository;
import com.licitacao.municipal.repository.MensagemConversaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class AnaliseService {

    private final PdfExtractorService pdfExtractorService;
    private final DiarioChunkRepository diarioChunkRepository;
    private final LeiRepository leiRepository;
    private final AnaliseRepository analiseRepository;
    private final MensagemConversaRepository mensagemConversaRepository;
    private final GeminiService geminiService;
    private final EmbeddingService embeddingService;
    private final MunicipioExtractorService municipioExtractorService;

    // =========================================================================
    // ANÁLISE INICIAL — Upload do PDF do cliente
    // =========================================================================

    @Transactional
    public Analise analisar(MultipartFile declaracaoCliente) throws IOException {
        String nomeDoArquivo = declaracaoCliente.getOriginalFilename();
        log.info("Iniciando análise inicial para: {}", nomeDoArquivo);

        // 1. Extrai o texto do PDF do cliente
        String textoCliente = pdfExtractorService.extrairTexto(
                declaracaoCliente.getInputStream(), nomeDoArquivo);

        // 2. Identifica o município — garante que o RAG busque apenas o conteúdo certo
        String municipio = municipioExtractorService.extrairMunicipio(textoCliente);
        log.info("Município identificado no PDF: '{}'",
                municipio != null ? municipio : "não identificado — busca global");

        // 3. Busca RAG filtrada: diários E leis do município correto
        RagResult rag = buscarRagCompleto(textoCliente, municipio);
        String statusContexto = descreverContexto(rag);

        // 4. Gera a defesa inicial via Gemini
        log.info("Enviando para Gemini. Diários: {}, Leis: {}",
                rag.quantidadeDiarios, rag.quantidadeLeis);
        String defesaInicial = geminiService.gerarDefesaInicial(textoCliente, rag.contexto);

        // 5. Persiste
        Analise analise = new Analise();
        analise.setNomeArquivo(nomeDoArquivo);
        analise.setTextoOriginalCliente(textoCliente);
        analise.setContextoRag(rag.contexto);
        analise.setResultadoDefesa(defesaInicial.trim());
        analise.setPdfTceUtilizado(statusContexto);
        analise.setPontosDeRisco("Defesa fundamentada em leis e jurisprudência local.");
        analise.setLeisUtilizadas(rag.leisUtilizadas);
        analise.setDiariosUtilizados(rag.diariosUtilizados);

        Analise salva = analiseRepository.save(analise);

        registrarMensagem(salva, MensagemConversa.RoleMensagem.ASSISTANT,
                "Defesa inicial gerada. Município: " + (municipio != null ? municipio : "não identificado")
                + " | Contexto: " + statusContexto);

        log.info("Análise inicial concluída. ID: {}", salva.getId());
        return salva;
    }

    // =========================================================================
    // REFINAMENTO INTERATIVO
    // =========================================================================

    @Transactional
    public Analise processarMensagem(Long analiseId, String instrucao) {
        Analise analise = analiseRepository.findById(analiseId)
                .orElseThrow(() -> new IllegalArgumentException("Análise não encontrada: " + analiseId));

        log.info("Processando instrução para caso {}: {}", analiseId, instrucao);

        registrarMensagem(analise, MensagemConversa.RoleMensagem.USER, instrucao);

        List<MensagemConversa> historicoAnterior = mensagemConversaRepository
                .findByAnaliseIdOrderByTimestampAsc(analiseId);

        String novaDefesa = geminiService.refinarDefesa(
                analise.getTextoOriginalCliente(),
                analise.getContextoRag(),
                analise.getResultadoDefesa(),
                historicoAnterior,
                instrucao
        );

        analise.setResultadoDefesa(novaDefesa.trim());
        Analise atualizada = analiseRepository.save(analise);

        registrarMensagem(atualizada, MensagemConversa.RoleMensagem.ASSISTANT, novaDefesa.trim());

        log.info("Defesa atualizada para o caso {}", analiseId);
        return atualizada;
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private void registrarMensagem(Analise analise, MensagemConversa.RoleMensagem role, String conteudo) {
        MensagemConversa msg = new MensagemConversa();
        msg.setAnalise(analise);
        msg.setRole(role);
        msg.setConteudo(conteudo);
        mensagemConversaRepository.save(msg);
    }

    public static class RagResult {
        public final String contexto;
        public final String leisUtilizadas;
        public final String diariosUtilizados;
        public final int quantidadeDiarios;
        public final int quantidadeLeis;

        public RagResult(String contexto, String leis, String diarios, int qtdDiarios, int qtdLeis) {
            this.contexto = contexto;
            this.leisUtilizadas = leis;
            this.diariosUtilizados = diarios;
            this.quantidadeDiarios = qtdDiarios;
            this.quantidadeLeis = qtdLeis;
        }
    }

    /**
     * Executa a busca RAG filtrada pelo município identificado no PDF do cliente.
     *
     * CORREÇÃO APLICADA NESTE MÉTODO:
     * - Diários TCE: filtrados por municipio (já existia)
     * - Leis: AGORA também filtradas por município (correção nova)
     *   → leis federais/estaduais (municipio IS NULL) sempre aparecem
     *   → leis municipais só aparecem se forem do município do caso
     */
    private RagResult buscarRagCompleto(String textoCliente, String municipio) {
        StringBuilder ctx = new StringBuilder();
        StringBuilder leis = new StringBuilder();

        String textoParaBusca = textoCliente.substring(0, Math.min(textoCliente.length(), 2000));
        double[] vetor = embeddingService.gerarVetorLocal(textoParaBusca);

        if (vetor == null) {
            log.warn("Não foi possível gerar vetor de busca. Retornando RAG vazio.");
            return new RagResult("", "", "", 0, 0);
        }

        String vetorStr = Arrays.toString(vetor);

        // ── DIÁRIOS TCE: filtrados por município ──────────────────────────────
        List<DiarioChunk> chunks;
        if (municipio != null && !municipio.isBlank()) {
            long totalChunksMunicipio = diarioChunkRepository.contarChunksPorMunicipio(municipio);
            if (totalChunksMunicipio > 0) {
                log.info("Buscando diários filtrados para '{}' ({} chunks disponíveis)", municipio, totalChunksMunicipio);
                chunks = diarioChunkRepository.buscarChunksPorMunicipioERelevancia(vetorStr, municipio, 10);
            } else {
                log.warn("'{}' identificado mas sem chunks no banco. Usando busca global.", municipio);
                chunks = diarioChunkRepository.buscarChunksMaisRelevantes(vetorStr, 10);
            }
        } else {
            chunks = diarioChunkRepository.buscarChunksMaisRelevantes(vetorStr, 10);
        }

        java.util.LinkedHashSet<String> datasUnicas = new java.util.LinkedHashSet<>();
        if (!chunks.isEmpty()) {
            ctx.append("=== JURISPRUDÊNCIA/PRECEDENTES DO TCE ===\n");
            for (DiarioChunk c : chunks) {
                String dataStr = c.getDataDiario().toString();
                ctx.append("DATA: ").append(dataStr).append("\n")
                   .append("TEXTO: ").append(c.getTexto()).append("\n\n");
                datasUnicas.add(dataStr);
            }
        }
        String diarios = String.join("|", datasUnicas);

        // ── LEIS: AGORA filtradas por município ───────────────────────────────
        // CORREÇÃO CRÍTICA: antes chamava buscarLeisMaisRelevantes() sem filtro,
        // o que trazia leis de Granja numa análise de Araripe.
        // Agora: se município identificado → busca leis do município + leis federais
        //        se município não identificado → busca global (fallback)
        List<Lei> leisList;
        if (municipio != null && !municipio.isBlank()) {
            log.info("Buscando leis filtradas para município '{}'", municipio);
            leisList = leiRepository.buscarLeisPorMunicipioERelevancia(vetorStr, municipio);
        } else {
            leisList = leiRepository.buscarLeisMaisRelevantes(vetorStr);
        }

        if (!leisList.isEmpty()) {
            ctx.append("=== LEGISLAÇÃO APLICÁVEL ===\n");
            for (Lei l : leisList) {
                ctx.append("LEI: ").append(l.getTitulo()).append("\n")
                   .append("REF: ").append(l.getReferencia()).append("\n")
                   .append("CONTEÚDO: ").append(l.getConteudo()).append("\n\n");
                if (leis.length() > 0) leis.append("|");
                leis.append(l.getTitulo()).append("§").append(l.getReferencia());
            }
        }

        log.info("RAG concluído: {} diários, {} leis (município: {})",
                datasUnicas.size(), leisList.size(),
                municipio != null ? municipio : "global");

        return new RagResult(ctx.toString(), leis.toString(), diarios, datasUnicas.size(), leisList.size());
    }

    private String descreverContexto(RagResult rag) {
        return "Diários TCE (" + rag.quantidadeDiarios + ") | Leis (" + rag.quantidadeLeis + ")";
    }

    // =========================================================================
    // LEITURA
    // =========================================================================

    public List<Analise> listarHistorico() {
        return analiseRepository.findAll();
    }

    public Analise buscarPorId(Long id) {
        return analiseRepository.findById(id).orElse(null);
    }

    public List<MensagemConversa> buscarConversa(Long analiseId) {
        return mensagemConversaRepository.findByAnaliseIdOrderByTimestampAsc(analiseId);
    }
}
