package com.licitacao.municipal.repository;

import com.licitacao.municipal.model.Lei;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Repository
public interface LeiRepository extends JpaRepository<Lei, Long> {

    @Query(value = "SELECT * FROM leis ORDER BY embedding <=> cast(?1 as vector) LIMIT 3", nativeQuery = true)
    List<Lei> buscarLeisMaisRelevantes(String vetorBusca);

    // Método para INSERIR forçando o cast do vetor
    @Modifying
    @Transactional
    @Query(value = "INSERT INTO leis (titulo, referencia, conteudo, embedding) VALUES (?1, ?2, ?3, cast(?4 as vector))", nativeQuery = true)
    void salvarLeiManual(String titulo, String referencia, String conteudo, String embeddingString);

    // NOVO: Método para ATUALIZAR forçando o cast do vetor
    @Modifying
    @Transactional
    @Query(value = "UPDATE leis SET titulo = ?2, referencia = ?3, conteudo = ?4, embedding = cast(?5 as vector) WHERE id = ?1", nativeQuery = true)
    void atualizarLeiManual(Long id, String titulo, String referencia, String conteudo, String embeddingString);
}