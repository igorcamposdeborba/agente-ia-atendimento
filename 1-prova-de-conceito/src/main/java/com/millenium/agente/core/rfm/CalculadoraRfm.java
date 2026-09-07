package com.millenium.agente.core.rfm;

import com.millenium.agente.core.model.Rfm;

import java.math.BigDecimal;

/**
 * Priorizacao RFM da Fase 1 com faixas fixas (PoC). Recencia pesa a inatividade (mais inativo =
 * mais urgente), "Frequencia" usa a antiguidade e Monetizacao usa o valor do contrato.
 * Os pesos vem da configuracao. Numa evolucao, as faixas podem virar quantis da propria base.
 */
public final class CalculadoraRfm {

    private final double pesoRecencia;
    private final double pesoAntiguidade;
    private final double pesoMonetizacao;

    public CalculadoraRfm(double pesoRecencia, double pesoAntiguidade, double pesoMonetizacao) {
        this.pesoRecencia = pesoRecencia;
        this.pesoAntiguidade = pesoAntiguidade;
        this.pesoMonetizacao = pesoMonetizacao;
    }

    public Rfm calcular(Long inatividadeMeses, long antiguidadeMeses, BigDecimal valorMensal) {
        int r = scoreRecencia(inatividadeMeses);
        int a = scoreAntiguidade(antiguidadeMeses);
        int m = scoreMonetizacao(valorMensal);
        double somaPesos = pesoRecencia + pesoAntiguidade + pesoMonetizacao;
        double score = (r * pesoRecencia + a * pesoAntiguidade + m * pesoMonetizacao) / somaPesos;
        return new Rfm(r, a, m, arredonda(score));
    }

    /** Nunca engajou (null) e o caso mais urgente. */
    private int scoreRecencia(Long inatividadeMeses) {
        if (inatividadeMeses == null) return 5;
        long m = inatividadeMeses;
        if (m >= 24) return 5;
        if (m >= 12) return 4;
        if (m >= 6) return 3;
        if (m >= 3) return 2;
        return 1;
    }

    private int scoreAntiguidade(long antiguidadeMeses) {
        if (antiguidadeMeses >= 60) return 5;
        if (antiguidadeMeses >= 48) return 4;
        if (antiguidadeMeses >= 36) return 3;
        if (antiguidadeMeses >= 24) return 2;
        return 1;
    }

    private int scoreMonetizacao(BigDecimal valorMensal) {
        if (valorMensal == null) return 1;
        double v = valorMensal.doubleValue();
        if (v >= 2000) return 5;
        if (v >= 1000) return 4;
        if (v >= 500) return 3;
        if (v >= 200) return 2;
        return 1;
    }

    private static double arredonda(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
