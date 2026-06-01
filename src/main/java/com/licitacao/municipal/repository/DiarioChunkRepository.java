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

    /**
     * Busca os chunks mais relevantes SEM filtro de município.
     * Usado como fallback quando o município não é identificado.
     */
    @Query(value = "SELECT * FROM diario_chunks ORDER BY embedding <=> cast(:vetorBusca as vector) LIMIT :limite", nativeQuery = true)
    List<DiarioChunk> buscarChunksMaisRelevantes(@Param("vetorBusca") String vetorBusca, @Param("limite") int limite);

    /**
     * Busca os chunks mais relevantes FILTRANDO por município (case-insensitive, busca parcial).
     * Corrige o bug onde o PDF de Araripe retornava chunks de Granja e outros municípios.
     * O ILIKE com % permite variações de escrita (ex: "Município de Araripe", "ARARIPE").
     */
    @Query(value = "SELECT * FROM diario_chunks WHERE municipio ILIKE '%' || :municipio || '%' ORDER BY embedding <=> cast(:vetorBusca as vector) LIMIT :limite", nativeQuery = true)
    List<DiarioChunk> buscarChunksPorMunicipioERelevancia(
            @Param("vetorBusca") String vetorBusca,
            @Param("municipio") String municipio,
            @Param("limite") int limite);

    /**
     * Conta quantos chunks existem para um determinado município.
     * Usado para decidir se o fallback sem filtro deve ser usado.
     */
    @Query(value = "SELECT COUNT(*) FROM diario_chunks WHERE municipio ILIKE '%' || :municipio || '%'", nativeQuery = true)
    long contarChunksPorMunicipio(@Param("municipio") String municipio);

    @Modifying
    @Transactional
    @Query(value = "INSERT INTO diario_chunks (data_diario, texto, indice_sequencia, municipio, embedding) VALUES (:dataDiario, :texto, :indiceSequencia, :municipio, cast(:embedding as vector))", nativeQuery = true)
    void salvarChunkManual(@Param("dataDiario") LocalDate dataDiario,
                           @Param("texto") String texto,
                           @Param("indiceSequencia") int indiceSequencia,
                           @Param("municipio") String municipio,
                           @Param("embedding") String embedding);

    /**
     * Mantém retrocompatibilidade: insere sem município (para dados legados já existentes).
     * @deprecated Use salvarChunkManual com municipio.
     */
    @Deprecated
    @Modifying
    @Transactional
    @Query(value = "INSERT INTO diario_chunks (data_diario, texto, indice_sequencia, embedding) VALUES (:dataDiario, :texto, :indiceSequencia, cast(:embedding as vector))", nativeQuery = true)
    void salvarChunkManualSemMunicipio(@Param("dataDiario") LocalDate dataDiario,
                           @Param("texto") String texto,
                           @Param("indiceSequencia") int indiceSequencia,
                           @Param("embedding") String embedding);
}
