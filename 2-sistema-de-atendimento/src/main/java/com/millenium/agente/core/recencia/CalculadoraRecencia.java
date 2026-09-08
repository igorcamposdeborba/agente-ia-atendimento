package com.millenium.agente.core.recencia;

import com.millenium.agente.core.model.SinalEngajamento;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Recencia consolidada: a ultima interacao e o MAX entre todos os sinais de engajamento do cliente.
 * <p>
 * Invariante central do sistema: <b>enviar != contato</b>. Como a lista so recebe sinais
 * originados no cliente (a construcao de {@link SinalEngajamento} e feita apenas para esses
 * eventos), disparos da Millenium nunca aparecem aqui e nunca reduzem a inatividade.
 */
public final class CalculadoraRecencia {

    private CalculadoraRecencia() {
    }

    public static Optional<LocalDate> ultimaInteracao(List<SinalEngajamento> sinais) {
        return sinais.stream()
                .map(SinalEngajamento::data)
                .max(Comparator.naturalOrder());
    }

    /** Meses de inatividade ate hoje; vazio quando nao ha nenhum sinal de engajamento. */
    public static Optional<Long> inatividadeMeses(List<SinalEngajamento> sinais, LocalDate hoje) {
        return ultimaInteracao(sinais)
                .map(ultima -> ChronoUnit.MONTHS.between(ultima, hoje));
    }
}
