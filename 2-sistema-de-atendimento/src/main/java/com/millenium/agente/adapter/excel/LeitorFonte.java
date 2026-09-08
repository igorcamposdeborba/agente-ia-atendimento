package com.millenium.agente.adapter.excel;

import java.nio.file.Path;
import java.util.List;

/**
 * Estrategia de leitura de uma planilha em linhas tipadas {@code T}. Implementacoes:
 * {@link LeitorCabecalho} (generico, por cabecalho, via Jackson) e {@link LeitorNps}
 * (especializacao posicional). Manter isto como interface deixa o {@link ExcelFonteDados}
 * declarativo e facil de estender (nova fonte = novo record + um leitor).
 */
@FunctionalInterface
public interface LeitorFonte<T> {
    List<T> ler(Path arquivo);
}
