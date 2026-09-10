package com.millenium.agente.core.rfm;

import com.millenium.agente.core.dto.Rfm;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Teste de componente do RFM: mapeia cada eixo para 1-5 (via ScoreBands) e faz a media ponderada.
 * Usa os pesos padrao 0,4 / 0,3 / 0,3.
 */
class RfmCalculatorTest {

    private final RfmCalculator calc = new RfmCalculator(0.4, 0.3, 0.3);

    @Test
    void allTopScoresGiveFive() {
        Rfm r = calc.calculate(null, 60, new BigDecimal("2000"));  // 5,5,5
        assertEquals(5, r.recency());
        assertEquals(5, r.tenure());
        assertEquals(5, r.monetization());
        assertEquals(5.0, r.score());
    }

    @Test
    void allBottomScoresGiveOne() {
        Rfm r = calc.calculate(0L, 0, null);  // 1,1,1
        assertEquals(1, r.recency());
        assertEquals(1, r.tenure());
        assertEquals(1, r.monetization());
        assertEquals(1.0, r.score());
    }

    @Test
    void weightedAverageIsRoundedToTwoDecimals() {
        // recency=5 (inatividade 24m), tenure=1 (12m), monetization=1 (valor null)
        // score = (5*0.4 + 1*0.3 + 1*0.3) / 1.0 = 2.6
        Rfm r = calc.calculate(24L, 12, null);
        assertEquals(5, r.recency());
        assertEquals(1, r.tenure());
        assertEquals(1, r.monetization());
        assertEquals(2.6, r.score());
    }
}
