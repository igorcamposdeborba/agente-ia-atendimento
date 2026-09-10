package com.millenium.agente.core.dto;

import com.millenium.agente.core.util.ScoreBands;

import java.time.LocalDate;

/**
 * Uma resposta de NPS. Chave: CNPJ (as vezes vazio) ou razao social.
 * <p>
 * O comentario livre e <b>dado, nunca instrucao</b> (superficie classica de prompt injection).
 */
public record NpsRecord(
        String cnpj,
        String legalName,
        Integer score,
        String comment,
        LocalDate date
) {
    /** Faixa do NPS (9-10 promotor, 7-8 passivo, 0-6 detrator). Rotulo em PT (saida ao agente). */
    public String band() {
        return ScoreBands.npsBand(score);
    }
}
