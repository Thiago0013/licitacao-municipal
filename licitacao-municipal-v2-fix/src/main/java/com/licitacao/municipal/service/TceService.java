package com.licitacao.municipal.service;

import com.licitacao.municipal.repository.DiarioChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class TceService {

    // Mantemos o geminiService aqui caso precise no futuro, mas não vamos usá-lo para vetores
    private final GeminiService geminiService;
    private final PdfExtractorService pdfExtractorService;
    private final TextSplitterService textSplitterService;
    private final DiarioChunkRepository diarioChunkRepository;

    // A nossa nova estrela: o motor local!
    private final EmbeddingService embeddingService;

    @Value("${tce.pdf.url}")
    private String urlTce;

    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void verificarECarregarBaseHistorica() {
        log.info("Iniciando Varredura Histórica Inteligente dos Diários Oficiais...");

        LocalDate dataVarredura = LocalDate.now();
        LocalDate dataLimitePassado = LocalDate.of(2020, 1, 1);

        int diasPulados = 0;
        int diasProcessados = 0;

        while (!dataVarredura.isBefore(dataLimitePassado)) {
            if (diarioChunkRepository.existsByDataDiario(dataVarredura)) {
                diasPulados++;
                dataVarredura = dataVarredura.minusDays(1);
                continue;
            }

            processarDiarioParaData(dataVarredura);
            diasProcessados++;
            dataVarredura = dataVarredura.minusDays(1);

            // Pausa de apenas 1 segundo entre DIAS (só para não derrubar o site do TCE com os downloads)
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
        }
        log.info("Varredura concluída! Novos: {}. Pulados: {}.", diasProcessados, diasPulados);
    }

    @Scheduled(cron = "${tce.scheduler.cron}")
    public void baixarDiarioDoDia() {
        LocalDate hoje = LocalDate.now();
        if (diarioChunkRepository.existsByDataDiario(hoje)) {
            log.info("O diário de hoje ({}) já existe no banco.", hoje);
            return;
        }
        processarDiarioParaData(hoje);
    }

    private void processarDiarioParaData(LocalDate data) {
        try {
            String dataFormatada = String.format("%02d/%02d/%04d", data.getDayOfMonth(), data.getMonthValue(), data.getYear());
            log.info("Processando data: {}", dataFormatada);

            Connection.Response resInicial = Jsoup.connect(urlTce).method(Connection.Method.GET).timeout(15000).execute();
            var cookies = resInicial.cookies();
            var docInicial = resInicial.parse();
            var form = docInicial.select("form").first();

            if (form == null) return;

            var dadosFormulario = new java.util.HashMap<String, String>();
            for (org.jsoup.nodes.Element input : form.select("input, select, textarea")) {
                if (!input.attr("name").isEmpty()) dadosFormulario.put(input.attr("name"), input.attr("value"));
            }

            dadosFormulario.put("incluirForm:dataEdicao_input", dataFormatada);
            dadosFormulario.put("diarioForm:dataInput", dataFormatada);

            Connection.Response resDownload = Jsoup.connect(urlTce).cookies(cookies).data(dadosFormulario)
                    .method(Connection.Method.POST).ignoreContentType(true).maxBodySize(0).timeout(30000).execute();

            if (resDownload.contentType() == null || !resDownload.contentType().contains("application/pdf")) {
                log.warn("Nenhum PDF retornado para a data {}", dataFormatada);
                return;
            }

            java.io.File diretorio = new java.io.File("./data/tce/");
            if (!diretorio.exists()) diretorio.mkdirs();
            java.io.File arquivoBaixado = new java.io.File(diretorio, "diario_" + data + ".pdf");

            try (java.io.FileOutputStream out = new java.io.FileOutputStream(arquivoBaixado)) {
                out.write(resDownload.bodyAsBytes());
            }

            String textoCompleto = pdfExtractorService.extrairTexto(arquivoBaixado);
            List<String> chunksTextuais = textSplitterService.splitText(textoCompleto);

            int indice = 1;
            for (String fatiaTexto : chunksTextuais) {
                try {
                    // MÁGICA ACONTECENDO AQUI: Chamamos a IA Local.
                    // Não tem mais sleep(), não tem mais try/catch de limite, é força bruta do seu PC!
                    double[] vetorMatematico = embeddingService.gerarVetorLocal(fatiaTexto);

                    if (vetorMatematico != null) {
                        diarioChunkRepository.salvarChunkManual(data, fatiaTexto, indice, java.util.Arrays.toString(vetorMatematico));
                    }
                } catch (Exception e) {
                    log.error("Falha ao gerar vetor ou salvar o chunk {} da data {}.", indice, data, e);
                }
                indice++;
            }

            arquivoBaixado.delete();
            log.info("Data {} finalizada com sucesso. {} chunks processados.", data, chunksTextuais.size());

        } catch (Exception e) {
            log.error("Erro fatal ao processar data {}: {}", data, e.getMessage());
        }
    }
}