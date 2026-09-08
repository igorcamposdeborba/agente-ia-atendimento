package com.millenium.agente.core.model;

import java.time.LocalDate;

/**
 * Um sinal de engajamento que <b>parte do cliente</b> (resposta de NPS, visita/atualizacao no N1,
 * mensagem RECEBIDA no Megazap, interacao registrada pelo cliente no People).
 * <p>
 * Regra invariavel do dominio: <b>enviar != contato</b>. Disparos feitos pela Millenium
 * (mensagem enviada no Megazap, disparo do People, fatura emitida) NUNCA viram um SinalEngajamento
 * e portanto nunca reduzem a inatividade. So se constroi esta classe para eventos originados no cliente.
 */
public record SinalEngajamento(
        FonteSinal fonte,
        LocalDate data,
        String descricao
) {
    public SinalEngajamento {
        if (fonte == null) throw new IllegalArgumentException("fonte obrigatoria");
        if (data == null) throw new IllegalArgumentException("data obrigatoria");
    }
}
