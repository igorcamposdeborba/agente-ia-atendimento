package com.millenium.agente.core.recencia;

import com.millenium.agente.core.dto.SignalSource;
import com.millenium.agente.core.dto.EngagementSignal;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Teste de componente da recencia consolidada (max dos sinais). A regra "enviar != contato" e
 * garantida na origem: so eventos do cliente viram {@link EngagementSignal}.
 */
class RecencyCalculatorTest {

    @Test
    void lastInteractionIsTheMostRecentSignal() {
        LocalDate today = LocalDate.of(2026, 1, 1);
        List<EngagementSignal> signals = List.of(
                new EngagementSignal(SignalSource.N1, LocalDate.of(2025, 3, 1), "visita"),
                new EngagementSignal(SignalSource.MEGAZAP, LocalDate.of(2025, 9, 1), "conversa"),
                new EngagementSignal(SignalSource.NPS, LocalDate.of(2025, 6, 1), "resposta")
        );
        assertEquals(LocalDate.of(2025, 9, 1), RecencyCalculator.lastInteraction(signals).orElseThrow());
        assertEquals(4L, RecencyCalculator.inactivityMonths(signals, today).orElseThrow());
    }

    @Test
    void noSignalsReturnsEmpty() {
        assertTrue(RecencyCalculator.lastInteraction(List.of()).isEmpty());
        assertTrue(RecencyCalculator.inactivityMonths(List.of(), LocalDate.now()).isEmpty());
    }
}
