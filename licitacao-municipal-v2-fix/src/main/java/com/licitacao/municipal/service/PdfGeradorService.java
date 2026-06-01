package com.licitacao.municipal.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;
import java.io.ByteArrayOutputStream;

@Service
public class PdfGeradorService {

    public byte[] gerarPdfDefesa(String nomeArquivo, String textoDefesa, String statusContexto) {
        // Define o tamanho A4 e as margens padrão de tribunais (2,5 cm superior/inferior, 2,0 cm laterais)
        Document document = new Document(PageSize.A4, 56, 56, 70, 70);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            // Fontes padronizadas para peças jurídicas
            Font fonteTitulo = FontFactory.getFont(FontFactory.TIMES_BOLD, 14);
            Font fonteSubTitulo = FontFactory.getFont(FontFactory.TIMES_BOLD, 12);
            Font fonteCorpo = FontFactory.getFont(FontFactory.TIMES, 12);
            Font fonteCitacaoLei = FontFactory.getFont(FontFactory.TIMES_ITALIC, 11);
            Font fonteAuditoria = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Font.NORMAL);

            // Divide o texto do Gemini por quebras de linha para processar elemento por elemento
            String[] linhas = textoDefesa.split("\n");

            for (String linha : linhas) {
                String linhaLimpa = linha.trim().replace("**", ""); // Remove marcações markdown se houver
                if (linhaLimpa.isEmpty()) continue;

                // Formata Títulos Principais (ex: EXCELENTÍSSIMO, DOS FATOS, DO PEDIDO)
                if (linhaLimpa.startsWith("EXCELENTÍSSIMO") || linhaLimpa.startsWith("I. ") || linhaLimpa.startsWith("II. ") || linhaLimpa.startsWith("III. ")) {
                    Paragraph titulo = new Paragraph(linhaLimpa, fonteTitulo);
                    titulo.setAlignment(Element.ALIGN_CENTER);
                    titulo.setSpacingBefore(18f);
                    titulo.setSpacingAfter(12f);
                    document.add(titulo);
                }
                // Formata Subtítulos de seções (ex: II.1, II.2)
                else if (linhaLimpa.startsWith("II.")) {
                    Paragraph subTitulo = new Paragraph(linhaLimpa, fonteSubTitulo);
                    subTitulo.setAlignment(Element.ALIGN_LEFT);
                    subTitulo.setSpacingBefore(12f);
                    subTitulo.setSpacingAfter(6f);
                    document.add(subTitulo);
                }
                // Formata Citações de Leis ou Artigos Recuados (linhas que começam com '>' ou aspas de bloco)
                else if (linhaLimpa.startsWith(">") || (linhaLimpa.startsWith("\"") && linhaLimpa.length() > 150)) {
                    String textoRecuado = linhaLimpa.replace(">", "").trim();
                    Paragraph citacao = new Paragraph(textoRecuado, fonteCitacaoLei);
                    citacao.setFirstLineIndent(0);
                    citacao.setIndentationLeft(113f); // Recuo regulamentar de 4cm da margem esquerda
                    citacao.setAlignment(Element.ALIGN_JUSTIFIED);
                    citacao.setSpacingAfter(12f);
                    document.add(citacao);
                }
                // Formata Parágrafos do Corpo da Petição
                else {
                    Paragraph paragrafo = new Paragraph(linhaLimpa, fonteCorpo);
                    paragrafo.setFirstLineIndent(56f); // Recuo de 2cm na primeira linha do parágrafo
                    paragrafo.setAlignment(Element.ALIGN_JUSTIFIED);
                    paragrafo.setSpacingAfter(10f);
                    document.add(paragrafo);
                }
            }

            // Bloco de rastreabilidade RAG inserido de forma discreta no fim do documento
            document.add(new Paragraph("\n\n"));
            Paragraph rodapeAuditoria = new Paragraph("PROCESSO DE AUDITORIA INTERNA - RAG PIPELINE\n" +
                    "Documento Analisado: " + nomeArquivo + "\n" +
                    "Mapeamento de Fontes: " + statusContexto, fonteAuditoria);
            rodapeAuditoria.setAlignment(Element.ALIGN_LEFT);
            document.add(rodapeAuditoria);

            document.close();
        } catch (DocumentException e) {
            e.printStackTrace();
        }

        return out.toByteArray();
    }
}