package com.millenium.agente.core.dto;

/**
 * Pontuacao RFM adaptada ao problema: Recencia (inatividade), "Frequencia" = Antiguidade
 * (tempo de casa) e Monetizacao (valor do contrato). Cada eixo de 1 a 5; o score final e a
 * media ponderada usada para priorizar quem contatar primeiro.
 */
public record Rfm(
        int recency,        // 5 = muito inativo (mais urgente de reativar)
        int tenure,         // 5 = cliente muito antigo
        int monetization,   // 5 = contrato de maior valor
        double score        // media ponderada (0-5)
) {
}
