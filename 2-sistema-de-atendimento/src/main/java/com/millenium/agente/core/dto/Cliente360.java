package com.millenium.agente.core.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Visao consolidada por cliente (o "Cliente 360"), montada em memoria a partir dos Excel.
 * Agrega as varias linhas do N1 (uma por produto) num unico cliente e pendura os sinais de
 * engajamento das demais fontes, com a marca de confianca do casamento.
 */
public record Cliente360(
        String cnpj,
        String razaoSocial,
        String representante,
        String telefone,
        BigDecimal valorMensalTotal,
        LocalDate primeiroContrato,
        String status,
        List<String> produtos,
        LocalDate ultimaVisita,          // proxy da ultima preventiva (nao ha coluna propria)
        List<SinalEngajamento> sinais,
        Integer npsNota,
        String npsComentario,
        LocalDate npsData,
        MatchConfianca confianca,
        List<String> fontesCasadas
) {
    public Optional<SinalEngajamento> sinalMaisRecente() {
        return sinais.stream().max(Comparator.comparing(SinalEngajamento::data));
    }

    public Optional<LocalDate> ultimaInteracao() {
        return sinalMaisRecente().map(SinalEngajamento::data);
    }

    /** CNPJ mascarado por padrao (minimizacao); mostra 2 primeiros e 2 ultimos digitos. */
    public String cnpjMascarado() {
        if (cnpj == null || cnpj.length() < 6) return "***";
        return cnpj.substring(0, 2) + ".***.***-" + cnpj.substring(cnpj.length() - 2);
    }
}
