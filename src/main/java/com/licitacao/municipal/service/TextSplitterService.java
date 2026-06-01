package com.licitacao.municipal.service;

import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

@Service
public class TextSplitterService {

    private static final int CHUNK_SIZE = 1500; // Tamanho de cada pedaço de texto
    private static final int CHUNK_OVERLAP = 200; // Interseção para não perder contexto

    public List<String> splitText(String text) {
        List<String> chunks = new ArrayList<>();

        if (text == null || text.trim().isEmpty()) {
            return chunks;
        }

        int textLength = text.length();
        int start = 0;

        while (start < textLength) {
            int end = Math.min(start + CHUNK_SIZE, textLength);

            // Extrai o pedaço de texto
            String chunk = text.substring(start, end);
            chunks.add(chunk.trim());

            // Se chegamos ao fim do texto, encerra o laço
            if (end == textLength) {
                break;
            }

            // Avança o ponteiro considerando a sobreposição (overlap)
            start += (CHUNK_SIZE - CHUNK_OVERLAP);
        }

        return chunks;
    }
}