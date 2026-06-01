package com.licitacao.municipal.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "leis")
public class Lei {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String titulo;
    private String referencia;

    @Column(columnDefinition = "TEXT")
    private String conteudo;

    /**
     * Município ao qual esta lei pertence.
     * Usado para filtrar a busca RAG e evitar que leis de Granja
     * apareçam numa análise de Araripe (e vice-versa).
     * NULL = lei federal/estadual (aplica a todos os municípios).
     */
    @Column(name = "municipio", length = 255)
    private String municipio;

    // Usando 384 dimensões (padrão do AllMiniLmL6V2 que instalamos)
    @Column(columnDefinition = "vector(384)")
    private String embedding;
}
