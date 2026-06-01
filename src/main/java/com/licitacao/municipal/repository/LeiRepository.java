package com.licitacao.municipal.repository;

import com.licitacao.municipal.model.Lei;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Repository
public interface LeiRepository extends JpaRepository<Lei, Long> {

    /**
     * Busca global sem filtro de município.
     * Usado como fallback quando o município não é identificado no PDF do cliente.
     */
    @Query(value = "SELECT * FROM leis ORDER BY embedding <=> cast(:vetorBusca as vector) LIMIT 3",
           nativeQuery = true)
    List<Lei> buscarLeisMaisRelevantes(@Param("vetorBusca") String vetorBusca);

    /**
     * Busca filtrada pelo município do caso:
     *  - Leis sem município (NULL) = leis federais/estaduais → sempre incluídas
     *  - Leis do município identificado no PDF do cliente → incluídas
     *  - Leis de outros municípios → EXCLUÍDAS
     *
     * Funciona para qualquer cidade do Ceará automaticamente.
     * O :municipio vem direto do MunicipioExtractorService, que detecta a cidade
     * a partir do documento enviado pelo cliente (Icó, Fortaleza, Araripe, etc.).
     */
    @Query(value = """
            SELECT * FROM leis
             WHERE municipio IS NULL
                OR municipio ILIKE '%' || :municipio || '%'
             ORDER BY embedding <=> cast(:vetorBusca as vector)
             LIMIT 5
            """, nativeQuery = true)
    List<Lei> buscarLeisPorMunicipioERelevancia(
            @Param("vetorBusca") String vetorBusca,
            @Param("municipio") String municipio);

    /**
     * Retorna todas as leis que ainda não têm município definido.
     * Usado pelo auto-tagger para corrigir leis legadas cadastradas antes
     * da correção do bug.
     */
    List<Lei> findByMunicipioIsNull();

    // ─────────────────────────────────────────────────────────────────────────
    // INSERT / UPDATE com cast explícito para o PGVector
    // ─────────────────────────────────────────────────────────────────────────

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO leis (titulo, referencia, conteudo, municipio, embedding)
            VALUES (?1, ?2, ?3, ?4, cast(?5 as vector))
            """, nativeQuery = true)
    void salvarLeiManual(String titulo, String referencia, String conteudo,
                         String municipio, String embeddingString);

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE leis
               SET titulo = ?2, referencia = ?3, conteudo = ?4,
                   municipio = ?5, embedding = cast(?6 as vector)
             WHERE id = ?1
            """, nativeQuery = true)
    void atualizarLeiManual(Long id, String titulo, String referencia, String conteudo,
                            String municipio, String embeddingString);

    /** @deprecated Use salvarLeiManual com municipio. */
    @Deprecated
    @Modifying
    @Transactional
    @Query(value = "INSERT INTO leis (titulo, referencia, conteudo, embedding) VALUES (?1, ?2, ?3, cast(?4 as vector))",
           nativeQuery = true)
    void salvarLeiManualSemMunicipio(String titulo, String referencia, String conteudo,
                                     String embeddingString);
}
