package com.licitacao.municipal.service;

import dev.langchain4j.model.embedding.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

@Service
public class EmbeddingService {

    // O LangChain4j já traz o modelo pronto para uso na memória!
    private final EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();

    public double[] gerarVetorLocal(String texto) {
        // Isso aqui roda na sua RAM/Processador. Custo ZERO e sem limites de internet!
        float[] vetorFloat = embeddingModel.embed(texto).content().vector();

        // Converte o float[] do LangChain4j para o double[] que o seu sistema já usa
        double[] vetorFinal = new double[vetorFloat.length];
        for (int i = 0; i < vetorFloat.length; i++) {
            vetorFinal[i] = vetorFloat[i];
        }

        return vetorFinal;
    }
}