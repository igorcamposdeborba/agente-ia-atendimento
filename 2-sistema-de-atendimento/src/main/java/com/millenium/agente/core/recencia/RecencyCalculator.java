package com.millenium.agente.core.recencia;

import com.millenium.agente.core.dto.EngagementSignal;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Recencia consolidada: a ultima interacao e o MAX entre todos os sinais de engajamento do cliente.
 * <p>
 * Invariante central do sistema: <b>enviar != contato</b>. Como a lista so recebe sinais
 * originados no cliente (a construcao de {@link EngagementSignal} e feita apenas para esses
 * eventos), disparos da Millenium nunca aparecem aqui e nunca reduzem a inatividade.
 */
public final class RecencyCalculator {

    private RecencyCalculator() {
    }

    public static Optional<LocalDate> lastInteraction(List<EngagementSignal> signals) {
        return signals.stream()
                .map(EngagementSignal::date)
                .max(Comparator.naturalOrder());
    }

    /** Meses de inatividade ate hoje; vazio quando nao ha nenhum sinal de engajamento. */
    public static Optional<Long> inactivityMonths(List<EngagementSignal> signals, LocalDate today) {
        return lastInteraction(signals)
                .map(last -> ChronoUnit.MONTHS.between(last, today));
    }
}
