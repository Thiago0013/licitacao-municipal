package com.licitacao.municipal.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;

@Entity
@Table(name = "diario_chunks")
@Data
public class DiarioChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "data_diario", nullable = false)
    private LocalDate dataDiario;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String texto;

    @Column(name = "indice_sequencia")
    private int indiceSequencia;

    /**
     * Nome do município ao qual este chunk pertence (extraído do PDF do diário).
     * Usado para filtrar a busca RAG e evitar que chunks de municípios errados
     * contaminem a análise de outro município.
     */
    @Column(name = "municipio", length = 255)
    private String municipio;

    @Column(columnDefinition = "vector(384)")
    private String embedding;

    public DiarioChunk() {}

    public DiarioChunk(LocalDate dataDiario, String texto, int indiceSequencia) {
        this.dataDiario = dataDiario;
        this.texto = texto;
        this.indiceSequencia = indiceSequencia;
    }
}
