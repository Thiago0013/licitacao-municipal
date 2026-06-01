package com.licitacao.municipal.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@Table(name = "analises")
public class Analise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nomeArquivo;

    private LocalDateTime dataAnalise;

    /** Texto completo do relatório/declaração do cliente — preservado para todas as rodadas. */
    @Column(columnDefinition = "TEXT")
    private String textoOriginalCliente;

    /** Contexto RAG (jurisprudência + leis) recuperado na análise inicial. */
    @Column(columnDefinition = "TEXT")
    private String contextoRag;

    /** Versão atual da defesa — atualizada a cada refinamento. */
    @Column(columnDefinition = "TEXT")
    private String resultadoDefesa;

    @Column(columnDefinition = "TEXT")
    private String pontosDeRisco;

    private String pdfTceUtilizado;

    /** Títulos/referências das leis usadas no RAG — separados por "|". */
    @Column(columnDefinition = "TEXT")
    private String leisUtilizadas;

    /** Datas dos diários TCE usados no RAG — separadas por "|". */
    @Column(columnDefinition = "TEXT")
    private String diariosUtilizados;

    /**
     * Histórico completo da conversa de refinamento.
     * Cada mensagem fica ordenada pelo timestamp.
     */
    @OneToMany(mappedBy = "analise", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @OrderBy("timestamp ASC")
    private List<MensagemConversa> conversa = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        this.dataAnalise = LocalDateTime.now();
    }
}
