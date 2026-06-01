package com.licitacao.municipal.service;

import com.licitacao.municipal.model.Analise;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;

@Service
public class PdfService {

    public byte[] gerarPdfDefesa(Analise analise) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 50, 50, 50, 50);

        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            document.open();

            Font fonteTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
            Font fonteSubtitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            Font fonteNormal = FontFactory.getFont(FontFactory.HELVETICA, 11);

            Paragraph cabecalho = new Paragraph("TRIBUNAL DE CONTAS DO ESTADO DO CEARÁ\nSISTEMA DE APOIO JURÍDICO MUNICIPAL\n\n", fonteTitulo);
            cabecalho.setAlignment(Element.ALIGN_CENTER);
            document.add(cabecalho);

            Paragraph info = new Paragraph();
            info.setFont(fonteNormal);
            info.add(new Chunk("PROCESSO: ", fonteSubtitulo));
            info.add("17601/2024-0 (Referência)\n");
            info.add(new Chunk("ARQUIVO ANALISADO: ", fonteSubtitulo));
            info.add(analise.getNomeArquivo() + "\n");
            info.add(new Chunk("DATA DA ANÁLISE: ", fonteSubtitulo));
            info.add(analise.getDataAnalise().toString() + "\n\n");
            document.add(info);

            Paragraph tituloPeca = new Paragraph("PETIÇÃO DE DEFESA ADMINISTRATIVA\n\n", fonteSubtitulo);
            tituloPeca.setAlignment(Element.ALIGN_CENTER);
            document.add(tituloPeca);

            String textoCompleto = analise.getResultadoDefesa();
            if (textoCompleto != null) {
                String[] linhas = textoCompleto.split("\n");
                for (String linha : linhas) {
                    if (linha.trim().isEmpty()) {
                        document.add(new Paragraph(" ", fonteNormal));
                    } else {
                        Paragraph p = new Paragraph(linha, fonteNormal);
                        p.setAlignment(Element.ALIGN_JUSTIFIED);
                        p.setSpacingAfter(6);
                        document.add(p);
                    }
                }
            }

            if (writer.getVerticalPosition(false) < 120) {
                document.newPage();
            }

            Paragraph blocoAssinatura = new Paragraph();
            blocoAssinatura.setAlignment(Element.ALIGN_CENTER);
            blocoAssinatura.setSpacingBefore(30);
            blocoAssinatura.add(new Paragraph("__________________________________________", fonteNormal));
            blocoAssinatura.add(new Paragraph("Assinatura do Responsável / Representante Legal", fonteNormal));
            document.add(blocoAssinatura);

            document.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return out.toByteArray();
    }
}