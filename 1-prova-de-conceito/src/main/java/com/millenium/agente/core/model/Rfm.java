package com.millenium.agente.core.model;

/**
 * Pontuacao RFM adaptada ao problema: Recencia (inatividade), "Frequencia" = Antiguidade
 * (tempo de casa) e Monetizacao (valor do contrato). Cada eixo de 1 a 5; o score final e a
 * media ponderada usada para priorizar quem contatar primeiro.
 */
public record Rfm(
        int recencia,       // 5 = muito inativo (mais urgente de reativar)
        int antiguidade,    // 5 = cliente muito antigo
        int monetizacao,    // 5 = contrato de maior valor
        double score        // media ponderada (0-5)
) {
}
