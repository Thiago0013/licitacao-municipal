package com.licitacao.municipal.dto;

import com.licitacao.municipal.model.MensagemConversa;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * DTO de saída para a API — representa o estado completo de um caso/análise
 * incluindo o texto atual da defesa e o histórico de chat.
 */
@Data
public class DefesaResponse {

    private Long id;
    private String nomeArquivo;
    private LocalDateTime dataAnalise;
    private String resultadoDefesa;
    private String pdfTceUtilizado;

    /** Leis recuperadas pelo RAG — cada item é um par "titulo|referencia". */
    private List<String> leisUtilizadas;

    /** Datas dos diários TCE recuperados pelo RAG. */
    private List<String> diariosUtilizados;

    private List<MensagemDto> conversa;

    @Data
    public static class MensagemDto {
        private Long id;
        private String role;   // "USER" ou "ASSISTANT"
        private String conteudo;
        private LocalDateTime timestamp;
    }

    /** Utilitário: converte uma MensagemConversa para MensagemDto. */
    public static MensagemDto toMensagemDto(MensagemConversa msg) {
        MensagemDto dto = new MensagemDto();
        dto.setId(msg.getId());
        dto.setRole(msg.getRole().name());
        dto.setConteudo(msg.getConteudo());
        dto.setTimestamp(msg.getTimestamp());
        return dto;
    }
}
