package com.millenium.agente.core.dto;

import java.time.LocalDate;

/**
 * Uma linha do export do People CRM. Chave: CNPJ.
 * A ultima interacao conta como engajamento do cliente.
 */
public record PeopleRecord(
        String cnpj,
        String legalName,
        String funnelStage,
        LocalDate lastInteraction
) {
}
