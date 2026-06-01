package com.licitacao.municipal.service;

import com.licitacao.municipal.dto.DefesaResponse;
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
import java.util.stream.Collectors;

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

    // =========================================================================
    // ANÁLISE INICIAL — Upload do PDF do cliente
    // =========================================================================

    /**
     * Fluxo completo de análise inicial:
     * 1. Extrai texto do PDF
     * 2. Busca RAG (diários TCE + leis)
     * 3. Gera a minuta de defesa inicial
     * 4. Persiste tudo e retorna a análise pronta para refinamento interativo
     */
    @Transactional
    public Analise analisar(MultipartFile declaracaoCliente) throws IOException {
        String nomeDoArquivo = declaracaoCliente.getOriginalFilename();
        log.info("Iniciando análise inicial para: {}", nomeDoArquivo);

        // 1. Extrai o texto do PDF do cliente
        String textoCliente = pdfExtractorService.extrairTexto(
                declaracaoCliente.getInputStream(), nomeDoArquivo);

        // 2. Busca vetorial RAG
        RagResult rag = buscarRagCompleto(textoCliente);
        String statusContexto = descreverContexto(textoCliente);

        // 3. Gera a defesa inicial via Gemini
        log.info("Enviando para geração de defesa inicial...");
        String defesaInicial = geminiService.gerarDefesaInicial(textoCliente, rag.contexto);

        // 4. Persiste a análise com o contexto RAG preservado para os turnos futuros
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

        // 5. Registra a primeira mensagem do assistente no histórico
        registrarMensagem(salva, MensagemConversa.RoleMensagem.ASSISTANT,
                "Defesa inicial gerada com base no documento enviado. " +
                "Contexto utilizado: " + statusContexto);

        log.info("Análise inicial concluída. ID: {}", salva.getId());
        return salva;
    }

    // =========================================================================
    // REFINAMENTO INTERATIVO — Chat com o estagiário sênior
    // =========================================================================

    /**
     * Processa uma instrução do advogado e atualiza a defesa.
     * A instrução pode ser qualquer comando curto, como:
     *   "Atenuar a multa — o município agiu de boa-fé"
     *   "Só preciso de um ofício de encaminhamento, sem discussão de mérito"
     *   "Aprofunde nos precedentes do TCU sobre dispensa de licitação"
     *
     * @param analiseId  ID do caso aberto
     * @param instrucao  Instrução do advogado
     * @return A análise com a defesa atualizada e o histórico completo
     */
    @Transactional
    public Analise processarMensagem(Long analiseId, String instrucao) {
        Analise analise = analiseRepository.findById(analiseId)
                .orElseThrow(() -> new IllegalArgumentException("Análise não encontrada: " + analiseId));

        log.info("Processando instrução para caso {}: {}", analiseId, instrucao);

        // Registra a instrução do advogado no histórico
        registrarMensagem(analise, MensagemConversa.RoleMensagem.USER, instrucao);

        // Recupera o histórico ANTES desta mensagem (para dar contexto ao modelo)
        List<MensagemConversa> historicoAnterior = mensagemConversaRepository
                .findByAnaliseIdOrderByTimestampAsc(analiseId);

        // Chama o Gemini para refinar com base na instrução + contexto completo
        String novaDefesa = geminiService.refinarDefesa(
                analise.getTextoOriginalCliente(),
                analise.getContextoRag(),
                analise.getResultadoDefesa(),
                historicoAnterior,
                instrucao
        );

        // Atualiza a defesa corrente
        analise.setResultadoDefesa(novaDefesa.trim());
        Analise atualizada = analiseRepository.save(analise);

        // Registra a resposta do sistema no histórico
        registrarMensagem(atualizada, MensagemConversa.RoleMensagem.ASSISTANT, novaDefesa.trim());

        log.info("Defesa atualizada com sucesso para o caso {}", analiseId);
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

    private String buscarContextoRag(String textoCliente) {
        return buscarRagCompleto(textoCliente).contexto;
    }

    /** Resultado interno do RAG com o contexto textual + metadados separados. */
    public static class RagResult {
        public final String contexto;
        public final String leisUtilizadas;   // pipe-separated "titulo|referencia"
        public final String diariosUtilizados; // pipe-separated dates

        public RagResult(String contexto, String leis, String diarios) {
            this.contexto = contexto;
            this.leisUtilizadas = leis;
            this.diariosUtilizados = diarios;
        }
    }

    private RagResult buscarRagCompleto(String textoCliente) {
        StringBuilder ctx = new StringBuilder();
        StringBuilder leis = new StringBuilder();

        String textoParaBusca = textoCliente.substring(0, Math.min(textoCliente.length(), 2000));
        double[] vetor = embeddingService.gerarVetorLocal(textoParaBusca);

        if (vetor != null) {
            String vetorStr = Arrays.toString(vetor);

            List<DiarioChunk> chunks = diarioChunkRepository.buscarChunksMaisRelevantes(vetorStr, 10);
            // Coleta datas únicas mantendo a ordem de relevância
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

            List<Lei> leisList = leiRepository.buscarLeisMaisRelevantes(vetorStr);
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

            return new RagResult(ctx.toString(), leis.toString(), diarios);
        }

        return new RagResult(ctx.toString(), leis.toString(), "");
    }

    private String descreverContexto(String textoCliente) {
        String textoParaBusca = textoCliente.substring(0, Math.min(textoCliente.length(), 2000));
        double[] vetor = embeddingService.gerarVetorLocal(textoParaBusca);
        if (vetor == null) return "Sem contexto RAG";

        String vetorStr = Arrays.toString(vetor);
        int nChunks = diarioChunkRepository.buscarChunksMaisRelevantes(vetorStr, 10).size();
        int nLeis = leiRepository.buscarLeisMaisRelevantes(vetorStr).size();
        return "Diários TCE (" + nChunks + ") | Leis (" + nLeis + ")";
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
