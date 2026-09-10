package com.millenium.agente.adapter.excel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Linhas tipadas de cada planilha do field mapping. Os quatro primeiros records sao desserializados
 * por {@link ExcelSpreadsheet#byHeader} (Jackson mapeia o cabecalho normalizado -> campo). O
 * {@link NpsRow} e a especializacao: o NPS e lido por posicao (colunas D/E) em
 * {@link ExcelDataSource} via {@link ExcelSpreadsheet#positional}, pois seus cabecalhos sao perguntas longas.
 * <p>
 * Os VALORES de {@code @JsonProperty} casam com as colunas reais do Excel (em portugues) e por isso
 * permanecem em PT. So os nomes dos campos foram traduzidos.
 */
final class SpreadsheetRows {
    private SpreadsheetRows() {
    }

    /** N1-Contatos (CADASTRO, prio 1) - identidade-mestre. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ContactsRow(
            @JsonProperty("cod") String code,
            @JsonProperty("nome") String legalName,
            @JsonProperty("fantasia") String tradeName,
            @JsonProperty("cnpjcpf") String cnpjCpf,
            @JsonProperty("telefone") String phone,
            @JsonProperty("email") String email) {
    }

    /** N1-Sistema (PRODUTO, prio 3) - contrato e vigencias; o cliente vem com prefixo "codigo- ". */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record SystemRow(
            @JsonProperty("cliente") String client,
            @JsonProperty("servico") String service,
            @JsonProperty("inicio vigencia") String startValidity,
            @JsonProperty("fim vigencia") String endValidity) {
    }

    /** N1-NF (NOTA_FISCAL, prio 2) - venda; valor do contrato = soma de todas as NF. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record InvoiceRow(
            @JsonProperty("pessoa") String person,
            @JsonProperty("valor total nf") String totalInvoiceValue,
            @JsonProperty("descricao") String description,
            @JsonProperty("data lancamento") String postingDate) {
    }

    /** Megazap (WHATSAPP, prio 4) - cada registro e uma conversa. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record MegazapRow(
            @JsonProperty("cnpj") String cnpj,
            @JsonProperty("telefone cliente") String clientPhone,
            @JsonProperty("e mail") String email,
            @JsonProperty("empresa") String company,
            @JsonProperty("data de criacao") String creationDate,
            @JsonProperty("problema") String problem) {
    }

    /**
     * NPS (prio 5) - especializacao: lido por POSICAO (o cabecalho e uma pergunta longa).
     * A=carimbo, C=empresa, D=nota, E=comentario (regra do field mapping secao 3.1).
     */
    record NpsRow(String timestamp, String company, String score, String comment) {
        static NpsRow fromColumns(String[] c) {
            return new NpsRow(
                    c.length > 0 ? c[0] : null,   // A - carimbo de data/hora
                    c.length > 2 ? c[2] : null,   // C - empresa
                    c.length > 3 ? c[3] : null,   // D - nota (0-10)
                    c.length > 4 ? c[4] : null);  // E - comentario
        }
    }
}
