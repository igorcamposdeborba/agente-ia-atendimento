package com.millenium.agente.core.model;

import java.time.LocalDate;

/**
 * Uma linha (mensagem) do export do Megazap (megazap_mensagens.xlsx). Chave: CNPJ.
 * Colunas: razao social, cnpj, data, direcao (recebida | enviada).
 * <p>
 * So mensagens <b>recebidas</b> contam como engajamento. "enviada" (disparo da Millenium) nunca
 * reduz a inatividade: enviar != contato.
 */
public record RegistroMegazap(
        String cnpj,
        String razaoSocial,
        LocalDate data,
        String direcao
) {
    public boolean recebida() {
        return direcao != null && direcao.strip().equalsIgnoreCase("recebida");
    }
}
