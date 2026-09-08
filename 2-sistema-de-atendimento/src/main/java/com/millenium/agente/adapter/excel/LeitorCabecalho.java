package com.millenium.agente.adapter.excel;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.List;

/**
 * Leitor generico por CABECALHO: cada linha vira um mapa {@code cabecalho normalizado -> valor} e o
 * <b>Jackson desserializa</b> no record {@code T} (os campos casam pelos {@code @JsonProperty}).
 * Serve para as planilhas cujos cabecalhos sao nomes de coluna (Contatos, Sistema, NF, Megazap).
 */
public final class LeitorCabecalho<T> implements LeitorFonte<T> {

    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final PlanilhaLeitor leitor;
    private final Class<T> tipo;

    public LeitorCabecalho(PlanilhaLeitor leitor, Class<T> tipo) {
        this.leitor = leitor;
        this.tipo = tipo;
    }

    @Override
    public List<T> ler(Path arquivo) {
        return leitor.comCabecalho(arquivo).stream()
                .map(linha -> JSON.convertValue(linha, tipo))
                .toList();
    }
}
