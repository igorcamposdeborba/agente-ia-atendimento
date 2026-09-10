package com.millenium.agente.core.dto;

import java.time.LocalDate;

/**
 * Um sinal de engajamento que <b>parte do cliente</b> (resposta de NPS, visita/atualizacao no N1,
 * atendimento registrado no Megazap, interacao registrada pelo cliente no People).
 * <p>
 * Regra invariavel do dominio: <b>enviar != contato</b>. Disparos feitos pela Millenium
 * (mensagem enviada no Megazap, disparo do People, fatura emitida) NUNCA viram um EngagementSignal
 * e portanto nunca reduzem a inatividade. So se constroi esta classe para eventos originados no cliente.
 */
public record EngagementSignal(
        SignalSource source,
        LocalDate date,
        String description
) {
    public EngagementSignal {
        if (source == null) throw new IllegalArgumentException("source obrigatoria");
        if (date == null) throw new IllegalArgumentException("date obrigatoria");
    }
}
