package com.millenium.agente.core.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Linha da fila priorizada do agente Preventivo (Saida A). Traz os sinais que justificam a linha,
 * o(s) gatilho(s) (A inatividade / B preventiva vencida) e a flag de conferencia humana.
 */
public record ClienteInativo(
        String cnpjMascarado,
        String razaoSocial,
        String representante,
        String telefone,
        BigDecimal valorMensalTotal,
        List<String> produtos,
        LocalDate ultimaInteracao,     // null = nenhum sinal de engajamento registrado
        Long inatividadeMeses,         // null quando nunca houve engajamento
        long antiguidadeMeses,
        boolean inatividadeGatilho,    // gatilho A
        boolean preventivaVencida,     // gatilho B
        Rfm rfm,
        MatchConfianca confianca,
        List<String> gatilhos,
        List<String> evidencias,
        boolean precisaConferenciaHumana
) {
}
