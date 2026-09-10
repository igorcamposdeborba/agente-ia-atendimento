package com.millenium.agente.core.dto;

import java.time.LocalDate;

/**
 * Um registro de atendimento do Megazap (WhatsApp). Chave: CNPJ ou telefone.
 * <p>
 * A planilha do Megazap <b>nao traz a resposta do cliente</b> mensagem a mensagem - traz o
 * <b>registro do atendimento</b>. Tratamos cada registro como uma <b>conversa</b>: houve interacao
 * real, entao conta como engajamento e reduz a inatividade (nao distinguimos "recebida x enviada").
 * O que nunca conta e um <b>disparo unilateral</b> (marketing/mensagem automatica), que sequer
 * aparece nesta planilha. A regra do dominio {@code enviar != conversa} continua valendo.
 */
public record MegazapRecord(
        String cnpj,
        String legalName,
        LocalDate date
) {
}
