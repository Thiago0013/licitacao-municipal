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

    // Usando 384 dimensões (padrão do AllMiniLmL6V2 que instalamos)
    @Column(columnDefinition = "vector(384)")
    private String embedding;
}