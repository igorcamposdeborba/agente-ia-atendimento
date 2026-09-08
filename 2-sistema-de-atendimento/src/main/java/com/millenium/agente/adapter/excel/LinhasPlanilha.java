package com.millenium.agente.adapter.excel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Linhas tipadas de cada planilha do field mapping. Os quatro primeiros records sao desserializados
 * pelo {@link LeitorCabecalho} (Jackson mapeia o cabecalho normalizado -> campo). O {@link LinhaNps}
 * e a <b>especializacao</b>: o NPS e lido por posicao (colunas D/E), pois seus cabecalhos sao
 * perguntas longas — ver {@link LeitorNps}.
 * <p>
 * Os campos ficam como texto cru; a conversao de datas/valores acontece na consolidacao
 * ({@link ExcelFonteDados}) com {@link PlanilhaLeitor#parseData}, {@code parseValor} e {@code parseInteiro}.
 * {@link JsonIgnoreProperties} torna a leitura tolerante a colunas extras.
 */
final class LinhasPlanilha {
    private LinhasPlanilha() {
    }

    /** N1-Contatos (CADASTRO, prio 1) — identidade-mestre. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record LinhaContatos(
            @JsonProperty("cod") String codigo,
            @JsonProperty("nome") String razaoSocial,
            @JsonProperty("fantasia") String nomeFantasia,
            @JsonProperty("cnpjcpf") String cnpjCpf,
            @JsonProperty("telefone") String telefone,
            @JsonProperty("email") String email) {
    }

    /** N1-Sistema (PRODUTO, prio 3) — contrato e vigencias; o cliente vem com prefixo "codigo- ". */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record LinhaSistema(
            @JsonProperty("cliente") String cliente,
            @JsonProperty("servico") String servico,
            @JsonProperty("inicio vigencia") String inicioVigencia,
            @JsonProperty("fim vigencia") String fimVigencia) {
    }

    /** N1-NF (NOTA_FISCAL, prio 2) — venda; valor do contrato = soma de todas as NF. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record LinhaNf(
            @JsonProperty("pessoa") String pessoa,
            @JsonProperty("valor total nf") String valorTotalNf,
            @JsonProperty("descricao") String descricao,
            @JsonProperty("data lancamento") String dataLancamento) {
    }

    /** Megazap (WHATSAPP, prio 4) — cada registro e uma conversa. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record LinhaMegazap(
            @JsonProperty("cnpj") String cnpj,
            @JsonProperty("telefone cliente") String telefoneCliente,
            @JsonProperty("e mail") String email,
            @JsonProperty("empresa") String empresa,
            @JsonProperty("data de criacao") String dataDeCriacao,
            @JsonProperty("problema") String problema) {
    }

    /**
     * NPS (prio 5) — <b>especializacao</b>: lido por POSICAO (o cabecalho e uma pergunta longa).
     * A=carimbo, C=empresa, D=nota, E=comentario (regra do field mapping §3.1).
     */
    record LinhaNps(String carimbo, String empresa, String nota, String comentario) {
        static LinhaNps deColunas(String[] c) {
            return new LinhaNps(
                    c.length > 0 ? c[0] : null,   // A - carimbo de data/hora
                    c.length > 2 ? c[2] : null,   // C - empresa
                    c.length > 3 ? c[3] : null,   // D - nota (0-10)
                    c.length > 4 ? c[4] : null);  // E - comentario
        }
    }
}
