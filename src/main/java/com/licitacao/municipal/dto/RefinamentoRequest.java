package com.licitacao.municipal.dto;

import lombok.Data;

/**
 * Payload recebido pelo endpoint de chat/refinamento.
 * O advogado envia uma instrução curta e direta para o sistema.
 *
 * Exemplos de instrução:
 *   "O município errou aqui, busque argumentos para atenuar a multa."
 *   "O Tribunal só pediu um documento. Faça apenas um ofício padrão de encaminhamento."
 *   "Aprofunde a pesquisa nas decisões do TCU sobre licitação emergencial."
 */
@Data
public class RefinamentoRequest {

    /** A instrução do advogado para refinar ou redirecionar a defesa. */
    private String instrucao;
}
