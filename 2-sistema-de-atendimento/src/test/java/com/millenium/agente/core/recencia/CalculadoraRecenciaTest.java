package com.millenium.agente.core.recencia;

import com.millenium.agente.core.model.FonteSinal;
import com.millenium.agente.core.model.SinalEngajamento;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalculadoraRecenciaTest {

    @Test
    void ultimaInteracaoEhOMaisRecenteEntreOsSinais() {
        LocalDate hoje = LocalDate.of(2026, 1, 1);
        List<SinalEngajamento> sinais = List.of(
                new SinalEngajamento(FonteSinal.N1, LocalDate.of(2025, 3, 1), "visita"),
                new SinalEngajamento(FonteSinal.MEGAZAP, LocalDate.of(2025, 9, 1), "recebida"),
                new SinalEngajamento(FonteSinal.NPS, LocalDate.of(2025, 6, 1), "resposta")
        );
        assertEquals(LocalDate.of(2025, 9, 1), CalculadoraRecencia.ultimaInteracao(sinais).orElseThrow());
        assertEquals(4L, CalculadoraRecencia.inatividadeMeses(sinais, hoje).orElseThrow());
    }

    @Test
    void semSinaisRetornaVazio() {
        assertTrue(CalculadoraRecencia.ultimaInteracao(List.of()).isEmpty());
        assertTrue(CalculadoraRecencia.inatividadeMeses(List.of(), LocalDate.now()).isEmpty());
    }
}
