package com.millenium.agente.core.util;

import java.math.BigDecimal;

/**
 * Faixas fixas da logica de pontuacao (Fase 1 / PoC), centralizadas para nao ficarem "hard coded"
 * espalhadas pelo codigo. Sao regras de ALGORITMO (nao parametros de negocio ajustaveis em runtime -
 * esses ficam em {@code MilleniumProperties}). Numa evolucao, estas faixas podem virar quantis da
 * propria base.
 * <ul>
 *   <li>RFM: cada eixo mapeia um valor para uma nota de 1 a 5;</li>
 *   <li>NPS: limiares das faixas promotor/passivo/detrator.</li>
 * </ul>
 */
public final class ScoreBands {

    private ScoreBands() {
    }

    // ---------- RFM: recencia (meses de inatividade -> nota) ----------
    /** Cortes de meses de inatividade, do mais urgente ao menos. */
    private static final int[] RECENCY_MONTHS = {24, 12, 6, 3};
    /** Cortes de tempo de casa (meses) para a "antiguidade". */
    private static final int[] TENURE_MONTHS = {60, 48, 36, 24};
    /** Cortes de valor (R$) para a "monetizacao". */
    private static final double[] MONETIZATION_VALUE = {2000, 1000, 500, 200};

    // ---------- NPS: limiares das faixas ----------
    public static final int NPS_PROMOTER_MIN = 9;   // 9-10 promotor
    public static final int NPS_PASSIVE_MIN = 7;    // 7-8 passivo; abaixo, detrator

    /** Nota de recencia (1-5). Nunca engajou (null) e o caso mais urgente (5). */
    public static int rfmRecency(Long inactivityMonths) {
        if (inactivityMonths == null) return 5;
        return scoreByDescending(inactivityMonths, RECENCY_MONTHS);
    }

    /** Nota de antiguidade (1-5) pelo tempo de casa em meses. */
    public static int rfmTenure(long tenureMonths) {
        return scoreByDescending(tenureMonths, TENURE_MONTHS);
    }

    /** Nota de monetizacao (1-5) pelo valor do contrato (soma das NF). */
    public static int rfmMonetization(BigDecimal value) {
        if (value == null) return 1;
        double v = value.doubleValue();
        for (int i = 0; i < MONETIZATION_VALUE.length; i++) {
            if (v >= MONETIZATION_VALUE[i]) return 5 - i;
        }
        return 1;
    }

    /** Faixa do NPS como rotulo em portugues (saida exibida ao agente). */
    public static String npsBand(Integer score) {
        if (score == null) return "sem nota";
        if (score >= NPS_PROMOTER_MIN) return "promotor";
        if (score >= NPS_PASSIVE_MIN) return "passivo";
        return "detrator";
    }

    /** Mapeia um valor para 5..1 conforme cai nos cortes (decrescentes); abaixo de todos, 1. */
    private static int scoreByDescending(long value, int[] cutoffs) {
        for (int i = 0; i < cutoffs.length; i++) {
            if (value >= cutoffs[i]) return 5 - i;
        }
        return 1;
    }
}
