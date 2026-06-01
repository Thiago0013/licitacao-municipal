package com.licitacao.municipal.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Representa uma mensagem individual no chat de refinamento da defesa.
 * Cada Analise pode ter múltiplas mensagens, formando o histórico
 * da conversa entre o advogado e o sistema.
 */
@Entity
@Data
@Table(name = "mensagens_conversa")
public class MensagemConversa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * A análise (caso) à qual esta mensagem pertence.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analise_id", nullable = false)
    private Analise analise;

    /**
     * Quem escreveu: USER (advogado) ou ASSISTANT (sistema de IA).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoleMensagem role;

    /**
     * O conteúdo da mensagem — instrução do advogado ou resposta/texto gerado.
     */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String conteudo;

    /**
     * Momento em que a mensagem foi criada.
     */
    private LocalDateTime timestamp;

    @PrePersist
    public void prePersist() {
        this.timestamp = LocalDateTime.now();
    }

    public enum RoleMensagem {
        USER,       // Instrução do advogado
        ASSISTANT   // Resposta/texto gerado pelo sistema
    }
}
