package com.millenium.agente.core.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Uma linha do export do N1 (n1_contratos.xlsx) - a espinha dorsal. Chave: CNPJ.
 * <p>
 * Atencao: o N1 tem <b>uma linha por produto/contrato</b>; um mesmo cliente aparece em varias
 * linhas. A consolidacao agrega por CNPJ (soma valor, junta produtos, pega as datas mais recentes).
 * Colunas: razao social, cnpj, produto, qtd, valor mensal, primeiro contrato, status contrato,
 * ultima visita, ult. atualiz. cadastral, ult. atualiz. contrato, representante, telefone.
 */
public record RegistroN1(
        String cnpj,
        String razaoSocial,
        String produto,
        Integer quantidade,
        BigDecimal valorMensal,
        LocalDate primeiroContrato,
        String statusContrato,
        LocalDate ultimaVisita,               // engajamento do cliente (e proxy de preventiva)
        LocalDate ultimaAtualizacaoCadastral, // engajamento do cliente
        LocalDate ultimaAtualizacaoContrato,  // engajamento do cliente
        String representante,
        String telefone
) {
}
