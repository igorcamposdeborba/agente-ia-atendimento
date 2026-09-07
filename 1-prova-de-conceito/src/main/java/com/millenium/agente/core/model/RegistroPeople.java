package com.millenium.agente.core.model;

import java.time.LocalDate;

/**
 * Uma linha do export do People CRM (people_crm.xlsx). Chave: CNPJ.
 * Colunas: razao social, cnpj, etapa funil, ultima interacao.
 * A ultima interacao conta como engajamento do cliente.
 */
public record RegistroPeople(
        String cnpj,
        String razaoSocial,
        String etapaFunil,
        LocalDate ultimaInteracao
) {
}
