package com.millenium.agente.core.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Uma linha do export do N1 - a espinha dorsal. Chave: CNPJ (ou codigo interno na falta dele).
 * <p>
 * Atencao: o N1 tem <b>uma linha por produto/contrato</b>; um mesmo cliente aparece em varias
 * linhas. A consolidacao agrega por chave (soma valor, junta produtos, pega as datas mais recentes).
 */
public record N1Record(
        String cnpj,
        String legalName,
        String product,
        Integer quantity,
        BigDecimal monthlyValue,
        LocalDate firstContract,
        String contractStatus,
        LocalDate lastVisit,                  // engajamento do cliente (e proxy de preventiva)
        LocalDate lastRegistrationUpdate,     // engajamento do cliente
        LocalDate lastContractUpdate,         // engajamento do cliente
        String representative,
        String phone
) {
}
