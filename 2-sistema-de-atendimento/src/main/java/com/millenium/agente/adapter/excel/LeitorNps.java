package com.millenium.agente.adapter.excel;

import com.millenium.agente.adapter.excel.LinhasPlanilha.LinhaNps;

import java.nio.file.Path;
import java.util.List;

/**
 * Especializacao para o NPS: o cabecalho do formulario e uma <b>pergunta longa</b>, entao a leitura
 * por cabecalho nao serve. Aqui a linha e lida por <b>posicao</b> (regra do field mapping §3.1:
 * nota = coluna D, comentario = coluna E), pulando a linha de cabecalho e respostas sem empresa.
 */
public final class LeitorNps implements LeitorFonte<LinhaNps> {

    private final PlanilhaLeitor leitor;

    public LeitorNps(PlanilhaLeitor leitor) {
        this.leitor = leitor;
    }

    @Override
    public List<LinhaNps> ler(Path arquivo) {
        return leitor.posicional(arquivo).stream()
                .skip(1)                                   // pula o cabecalho (pergunta longa)
                .map(LinhaNps::deColunas)
                .filter(n -> n.empresa() != null && !n.empresa().isBlank())
                .toList();
    }
}
