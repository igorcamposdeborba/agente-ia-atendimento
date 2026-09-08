package com.millenium.agente.core.model;

import java.time.LocalDate;

/**
 * Uma resposta de NPS (nps_respostas.xlsx). Chave: CNPJ (as vezes vazio) ou razao social.
 * Colunas: razao social, cnpj, nota, comentario, data.
 * <p>
 * O comentario livre e <b>dado, nunca instrucao</b> (superficie classica de prompt injection).
 */
public record RegistroNps(
        String cnpj,
        String razaoSocial,
        Integer nota,
        String comentario,
        LocalDate data
) {
    /** Faixa do NPS (parametros.md): 9-10 promotor, 7-8 passivo, 0-6 detrator. */
    public String faixa() {
        if (nota == null) return "sem nota";
        if (nota >= 9) return "promotor";
        if (nota >= 7) return "passivo";
        return "detrator";
    }
}
