package com.millenium.agente.core.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Visao consolidada por cliente (o "Cliente 360"), montada em memoria a partir dos Excel.
 * Agrega as varias linhas do N1 (uma por produto) num unico cliente e pendura os sinais de
 * engajamento das demais fontes, com a marca de confianca do casamento.
 */
public record Client360(
        String cnpj,
        String legalName,
        String representative,
        String phone,
        BigDecimal totalValue,
        LocalDate firstContract,
        String status,
        List<String> products,
        LocalDate lastVisit,             // proxy da ultima preventiva (nao ha coluna propria)
        List<EngagementSignal> signals,
        Integer npsScore,
        String npsComment,
        LocalDate npsDate,
        MatchConfidence confidence,
        List<String> matchedSources
) {
    /** CNPJ mascarado por padrao (minimizacao); mostra 2 primeiros e 2 ultimos digitos. */
    public String maskedCnpj() {
        if (cnpj == null || cnpj.length() < 6) return "***";
        return cnpj.substring(0, 2) + ".***.***-" + cnpj.substring(cnpj.length() - 2);
    }
}
