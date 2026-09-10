package com.millenium.agente.core.rfm;

import com.millenium.agente.core.dto.Rfm;
import com.millenium.agente.core.util.ScoreBands;

import java.math.BigDecimal;

/**
 * Priorizacao RFM da Fase 1 com faixas fixas (PoC). Recencia pesa a inatividade (mais inativo =
 * mais urgente), "Frequencia" usa a antiguidade e Monetizacao usa o valor do contrato.
 * Os pesos vem da configuracao; as faixas vem de {@link ScoreBands}.
 */
public final class RfmCalculator {

    private final double recencyWeight;
    private final double tenureWeight;
    private final double monetizationWeight;

    public RfmCalculator(double recencyWeight, double tenureWeight, double monetizationWeight) {
        this.recencyWeight = recencyWeight;
        this.tenureWeight = tenureWeight;
        this.monetizationWeight = monetizationWeight;
    }

    public Rfm calculate(Long inactivityMonths, long tenureMonths, BigDecimal totalValue) {
        int r = ScoreBands.rfmRecency(inactivityMonths);
        int a = ScoreBands.rfmTenure(tenureMonths);
        int m = ScoreBands.rfmMonetization(totalValue);
        double weightSum = recencyWeight + tenureWeight + monetizationWeight;
        double score = (r * recencyWeight + a * tenureWeight + m * monetizationWeight) / weightSum;
        return new Rfm(r, a, m, round(score));
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
