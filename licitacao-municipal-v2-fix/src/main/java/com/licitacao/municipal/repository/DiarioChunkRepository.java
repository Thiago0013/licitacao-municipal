package com.licitacao.municipal.repository;

import com.licitacao.municipal.model.DiarioChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface DiarioChunkRepository extends JpaRepository<DiarioChunk, Long> {

    List<DiarioChunk> findByDataDiario(LocalDate dataDiario);

    boolean existsByDataDiario(LocalDate dataDiario);

    @Query(value = "SELECT * FROM diario_chunks ORDER BY embedding <=> cast(:vetorBusca as vector) LIMIT :limite", nativeQuery = true)
    List<DiarioChunk> buscarChunksMaisRelevantes(@Param("vetorBusca") String vetorBusca, @Param("limite") int limite);

    // O MÉTODO QUE ESTAVA FALTANDO AQUI:
    @Modifying
    @Transactional
    @Query(value = "INSERT INTO diario_chunks (data_diario, texto, indice_sequencia, embedding) VALUES (:dataDiario, :texto, :indiceSequencia, cast(:embedding as vector))", nativeQuery = true)
    void salvarChunkManual(@Param("dataDiario") LocalDate dataDiario,
                           @Param("texto") String texto,
                           @Param("indiceSequencia") int indiceSequencia,
                           @Param("embedding") String embedding);

}