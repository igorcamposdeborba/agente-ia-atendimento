package com.millenium.agente.core.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Teste de componente das faixas de pontuacao (muitas combinacoes de corte). Fixa o comportamento
 * das faixas de RFM e das faixas de NPS num unico ponto (evita "numeros magicos" espalhados).
 */
class ScoreBandsTest {

    @Test
    void rfmRecencyBands() {
        assertEquals(5, ScoreBands.rfmRecency(null), "nunca engajou = mais urgente");
        assertEquals(5, ScoreBands.rfmRecency(24L));
        assertEquals(4, ScoreBands.rfmRecency(23L));
        assertEquals(4, ScoreBands.rfmRecency(12L));
        assertEquals(3, ScoreBands.rfmRecency(11L));
        assertEquals(3, ScoreBands.rfmRecency(6L));
        assertEquals(2, ScoreBands.rfmRecency(5L));
        assertEquals(2, ScoreBands.rfmRecency(3L));
        assertEquals(1, ScoreBands.rfmRecency(2L));
        assertEquals(1, ScoreBands.rfmRecency(0L));
    }

    @Test
    void rfmTenureBands() {
        assertEquals(5, ScoreBands.rfmTenure(60));
        assertEquals(4, ScoreBands.rfmTenure(48));
        assertEquals(3, ScoreBands.rfmTenure(36));
        assertEquals(2, ScoreBands.rfmTenure(24));
        assertEquals(1, ScoreBands.rfmTenure(23));
    }

    @Test
    void rfmMonetizationBands() {
        assertEquals(1, ScoreBands.rfmMonetization(null));
        assertEquals(5, ScoreBands.rfmMonetization(new BigDecimal("2000")));
        assertEquals(4, ScoreBands.rfmMonetization(new BigDecimal("1000")));
        assertEquals(3, ScoreBands.rfmMonetization(new BigDecimal("500")));
        assertEquals(2, ScoreBands.rfmMonetization(new BigDecimal("200")));
        assertEquals(1, ScoreBands.rfmMonetization(new BigDecimal("199.99")));
    }

    @Test
    void npsBands() {
        assertEquals("sem nota", ScoreBands.npsBand(null));
        assertEquals("promotor", ScoreBands.npsBand(10));
        assertEquals("promotor", ScoreBands.npsBand(9));
        assertEquals("passivo", ScoreBands.npsBand(8));
        assertEquals("passivo", ScoreBands.npsBand(7));
        assertEquals("detrator", ScoreBands.npsBand(6));
        assertEquals("detrator", ScoreBands.npsBand(0));
    }
}
