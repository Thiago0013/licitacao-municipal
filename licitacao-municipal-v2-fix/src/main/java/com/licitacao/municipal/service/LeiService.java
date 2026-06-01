package com.licitacao.municipal.service;

import com.licitacao.municipal.model.Lei;
import com.licitacao.municipal.repository.LeiRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeiService {

    private final LeiRepository leiRepository;
    private final EmbeddingService embeddingService;

    // CREATE (Já existia)
    public Lei cadastrarLei(String titulo, String referencia, String conteudo) {
        double[] vetor = embeddingService.gerarVetorLocal(titulo + " " + conteudo);
        String embeddingString = Arrays.toString(vetor);
        leiRepository.salvarLeiManual(titulo, referencia, conteudo, embeddingString);

        log.info("Nova lei cadastrada com sucesso: {}", titulo);
        return new Lei(); // Retorno fictício apenas para status 200
    }

    // READ ALL
    public List<Lei> listarTodas() {
        return leiRepository.findAll();
    }

    // READ ONE
    public Lei buscarPorId(Long id) {
        return leiRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Lei não encontrada com ID: " + id));
    }

    // UPDATE
    public Lei atualizarLei(Long id, String titulo, String referencia, String conteudo) {
        // Verifica se existe antes de atualizar
        buscarPorId(id);

        // Como o texto mudou, precisamos recalcular o mapa vetorial (embedding)
        log.info("Recalculando vetor e atualizando a lei ID: {}", id);
        double[] vetorNovo = embeddingService.gerarVetorLocal(titulo + " " + conteudo);
        String embeddingString = Arrays.toString(vetorNovo);

        // Salva usando o método manual do repositório para evitar erro de tipo no PGVector
        leiRepository.atualizarLeiManual(id, titulo, referencia, conteudo, embeddingString);

        return buscarPorId(id); // Retorna a lei atualizada
    }

    // DELETE
    public void deletarLei(Long id) {
        buscarPorId(id); // Valida se existe
        leiRepository.deleteById(id);
        log.info("Lei deletada com sucesso ID: {}", id);
    }
}