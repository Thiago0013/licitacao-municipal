package com.licitacao.municipal.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

@Service
@Slf4j
public class PdfExtractorService {

    /**
     * Extrai o texto COMPLETO de um arquivo PDF no disco (Para o Robô do TCE)
     */
    public String extrairTexto(File arquivo) {
        try (PDDocument doc = Loader.loadPDF(arquivo)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String texto = stripper.getText(doc);
            log.info("PDF extraído com sucesso: {} | {} caracteres totais", arquivo.getName(), texto.length());

            // REMOVIDO O TRUNCAR DAQUI: Precisamos de 100% do texto para o nosso banco de vetores!
            return texto;
        } catch (IOException e) {
            log.error("Erro ao extrair texto do PDF: {}", arquivo.getName(), e);
            return "";
        }
    }

    /**
     * Extrai texto de um InputStream (Upload de petição/relatório do usuário)
     */
    public String extrairTexto(InputStream inputStream, String nomeArquivo) {
        try (PDDocument doc = Loader.loadPDF(inputStream.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String texto = stripper.getText(doc);
            log.info("PDF extraído com sucesso: {} | {} caracteres", nomeArquivo, texto.length());

            // Aqui podemos manter o truncar se o relatório do usuário não puder passar de um limite
            return truncar(texto, 8000);
        } catch (IOException e) {
            log.error("Erro ao extrair texto do PDF: {}", nomeArquivo, e);
            return "";
        }
    }

    private String truncar(String texto, int maxChars) {
        if (texto == null) return "";
        if (texto.length() <= maxChars) return texto;
        return texto.substring(0, maxChars) + "\n[... texto truncado ...]";
    }
}