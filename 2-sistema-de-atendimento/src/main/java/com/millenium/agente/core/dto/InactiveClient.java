package com.millenium.agente.core.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Linha da fila priorizada do agente Preventivo (Saida A). Traz os sinais que justificam a linha,
 * o(s) gatilho(s) (A inatividade / B preventiva vencida) e a flag de conferencia humana.
 */
public record InactiveClient(
        String maskedCnpj,
        String legalName,
        String representative,
        String phone,
        BigDecimal totalValue,
        List<String> products,
        LocalDate lastInteraction,     // null = nenhum sinal de engajamento registrado
        Long inactivityMonths,         // null quando nunca houve engajamento
        long tenureMonths,
        boolean inactivityTrigger,     // gatilho A
        boolean maintenanceOverdue,    // gatilho B
        Rfm rfm,
        MatchConfidence confidence,
        List<String> triggers,
        List<String> evidence,
        boolean needsHumanReview
) {
}
