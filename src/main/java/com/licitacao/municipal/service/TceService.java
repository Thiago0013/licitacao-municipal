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

    private final GeminiService geminiService;
    private final PdfExtractorService pdfExtractorService;
    private final TextSplitterService textSplitterService;
    private final DiarioChunkRepository diarioChunkRepository;
    private final EmbeddingService embeddingService;
    private final MunicipioExtractorService municipioExtractorService; // FIX Bug 2: para taggear chunks

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

            // FIX Bug 2: o diário do TCE-CE pode conter decisões de múltiplos municípios.
            // Para cada chunk, extraímos o município específico do trecho.
            // Se o chunk não identificar município, usamos o do cabeçalho geral do diário.
            String municipioGeral = municipioExtractorService.extrairMunicipio(
                    textoCompleto.substring(0, Math.min(textoCompleto.length(), 3000)));

            int indice = 1;
            int chunksComMunicipio = 0;
            for (String fatiaTexto : chunksTextuais) {
                try {
                    double[] vetorMatematico = embeddingService.gerarVetorLocal(fatiaTexto);

                    if (vetorMatematico != null) {
                        // Tenta identificar o município no chunk; usa o geral como fallback
                        String municipioChunk = municipioExtractorService.extrairMunicipio(fatiaTexto);
                        if (municipioChunk == null) {
                            municipioChunk = municipioGeral;
                        }
                        if (municipioChunk != null) chunksComMunicipio++;

                        // FIX Bug 2: salva com o campo municipio preenchido
                        diarioChunkRepository.salvarChunkManual(
                                data, fatiaTexto, indice,
                                municipioChunk,  // pode ser null — banco aceita nullable
                                java.util.Arrays.toString(vetorMatematico));
                    }
                } catch (Exception e) {
                    log.error("Falha ao gerar vetor ou salvar o chunk {} da data {}.", indice, data, e);
                }
                indice++;
            }

            arquivoBaixado.delete();
            log.info("Data {} finalizada: {} chunks processados, {} com município identificado.",
                    data, chunksTextuais.size(), chunksComMunicipio);

        } catch (Exception e) {
            log.error("Erro fatal ao processar data {}: {}", data, e.getMessage());
        }
    }
}
