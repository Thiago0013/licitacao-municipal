package com.licitacao.municipal.service;

import com.licitacao.municipal.model.MensagemConversa;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class GeminiService {

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.embedding-url}")
    private String embeddingUrl;

    private final WebClient webClient;

    // Prompt de sistema reutilizado em todos os turnos — o "DNA" do estagiário sênior
    private static final String SISTEMA_PROMPT = """
        Você é um Assessor Jurídico Sênior especialista em Direito Administrativo, \
        Licitações Públicas e Controle Externo (TCE/TCU). \
        Você conhece profundamente a Lei 8.666/1993, a Lei 14.133/2021 (Nova Lei de Licitações), \
        a Lei de Responsabilidade Fiscal e as jurisprudências consolidadas dos Tribunais de Contas. \
        Você trabalha exclusivamente para advogados municipais e nunca inventa dados ou precedentes. \
        Se não houver jurisprudência relevante no contexto fornecido, \
        baseia-se na legislação e nos fatos do caso. Tom: formal, impessoal e técnico.
        """;

    public GeminiService(WebClient.Builder builder) {
        this.webClient = builder.build();
    }

    // =========================================================================
    // GERAÇÃO INICIAL DA DEFESA (primeiro turno — chamado pelo AnaliseService)
    // =========================================================================

    /**
     * Gera a minuta inicial de defesa com base no relatório do cliente e no
     * contexto RAG (jurisprudências + leis recuperadas pelo sistema).
     */
    public String gerarDefesaInicial(String textoCliente, String contextoRag) {
        String prompt = """
            [CONTEXTO DO SISTEMA]
            %s

            [CASO DO MUNICÍPIO CLIENTE]
            %s

            [CONHECIMENTO HISTÓRICO DO TCE E LEGISLAÇÃO APLICÁVEL]
            %s

            [INSTRUÇÃO]
            Com base nos documentos acima, redija uma minuta completa de defesa técnica estruturada em:
            1. DOS FATOS
            2. FUNDAMENTAÇÃO JURÍDICA (cite as leis e precedentes do contexto; se não houver jurisprudência útil, ignore-a)
            3. DO PEDIDO

            Nunca invente números de processos, datas ou decisões que não estejam no contexto fornecido.
            """.formatted(SISTEMA_PROMPT, textoCliente, contextoRag);

        return chamarGeminiTexto(prompt);
    }

    // =========================================================================
    // REFINAMENTO INTERATIVO (turnos subsequentes — chamado pelo AnaliseService)
    // =========================================================================

    /**
     * Refina a defesa com base em uma instrução do advogado.
     * Recebe o contexto completo (caso original + RAG) e o histórico da conversa
     * para que o modelo nunca perca o fio da meada.
     *
     * @param textoOriginalCliente  O relatório/declaração original do município
     * @param contextoRag           Jurisprudências e leis recuperadas na análise inicial
     * @param defesaAtual           A versão mais recente da defesa (pode ter sido refinada antes)
     * @param historico             Histórico completo de instruções e respostas anteriores
     * @param instrucaoAtual        A nova instrução do advogado neste turno
     */
    public String refinarDefesa(
            String textoOriginalCliente,
            String contextoRag,
            String defesaAtual,
            List<MensagemConversa> historico,
            String instrucaoAtual
    ) {
        // Monta o bloco de histórico anterior (se existir) para dar memória ao modelo
        StringBuilder historicoFormatado = new StringBuilder();
        if (historico != null && !historico.isEmpty()) {
            historicoFormatado.append("\n[HISTÓRICO DESTA SESSÃO DE REFINAMENTO]\n");
            for (MensagemConversa msg : historico) {
                String prefixo = msg.getRole() == MensagemConversa.RoleMensagem.USER
                        ? "ADVOGADO" : "SISTEMA";
                // Para o ASSISTANT, inclui apenas um resumo curto para não estourar o contexto
                String conteudo = msg.getRole() == MensagemConversa.RoleMensagem.ASSISTANT
                        ? resumirParaHistorico(msg.getConteudo())
                        : msg.getConteudo();
                historicoFormatado.append(prefixo).append(": ").append(conteudo).append("\n\n");
            }
        }

        String prompt = """
            [CONTEXTO DO SISTEMA]
            %s

            [CASO DO MUNICÍPIO CLIENTE — DOCUMENTO ORIGINAL]
            %s

            [BASE JURÍDICA DISPONÍVEL (TCE + LEGISLAÇÃO)]
            %s
            %s
            [VERSÃO ATUAL DA DEFESA]
            %s

            [NOVA INSTRUÇÃO DO ADVOGADO]
            %s

            [INSTRUÇÃO CRÍTICA]
            Execute a instrução acima com precisão. Interprete-a como um comando direto:
            - Se pedir "atenuar a multa" → busque no contexto argumentos de proporcionalidade, boa-fé e reparação
            - Se pedir "apenas ofício" ou "só encaminhamento" → gere SOMENTE o ofício, sem discutir o mérito
            - Se pedir "aprofundar TCU" → concentre a fundamentação nos precedentes do TCU presentes no contexto
            - Se pedir reescrita de uma seção específica → reescreva apenas aquela seção
            - Para qualquer outro comando → interprete e execute com bom senso jurídico

            Retorne o documento completo e atualizado (ou apenas o trecho solicitado, se for uma instrução parcial).
            Nunca invente dados, datas ou números de processo.
            """.formatted(
                SISTEMA_PROMPT,
                textoOriginalCliente,
                contextoRag,
                historicoFormatado.toString(),
                defesaAtual,
                instrucaoAtual
            );

        return chamarGeminiTexto(prompt);
    }

    // =========================================================================
    // MÉTODO HTTP INTERNO
    // =========================================================================

    @SuppressWarnings("unchecked")
    public String chamarGeminiTexto(String prompt) {
        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", prompt)))
                )
        );

        try {
            String urlTexto = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + apiKey.trim();
            java.net.URI uriSegura = java.net.URI.create(urlTexto);

            Map response = webClient.post()
                    .uri(uriSegura)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            List candidates = (List) response.get("candidates");
            Map firstCandidate = (Map) candidates.get(0);
            Map content = (Map) firstCandidate.get("content");
            List parts = (List) content.get("parts");
            Map firstPart = (Map) parts.get(0);

            return (String) firstPart.get("text");

        } catch (Exception e) {
            log.error("Erro ao chamar o Gemini", e);
            return "Falha ao gerar resposta: " + e.getMessage();
        }
    }

    /**
     * Reduz textos longos para inclusão no histórico, evitando estourar o contexto do modelo.
     * Mantém o início (que geralmente tem a estrutura do documento) e corta o restante.
     */
    private String resumirParaHistorico(String texto) {
        if (texto == null || texto.length() <= 500) return texto;
        return texto.substring(0, 500) + " [...resposta truncada no histórico...]";
    }
}
