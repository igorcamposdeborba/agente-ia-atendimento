package com.millenium.agente.core.linkage;

import com.millenium.agente.core.dto.Client360;
import com.millenium.agente.core.dto.SignalSource;
import com.millenium.agente.core.dto.MatchConfidence;
import com.millenium.agente.core.dto.MegazapRecord;
import com.millenium.agente.core.dto.N1Record;
import com.millenium.agente.core.dto.NpsRecord;
import com.millenium.agente.core.dto.PeopleRecord;
import com.millenium.agente.core.recencia.RecencyCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Teste de componente do cruzamento (entity resolution): junta linhas do N1 e pendura
 * Megazap/NPS/People, marcando a confianca do casamento.
 */
class EntityResolverTest {

    private final EntityResolver resolver = new EntityResolver();

    private N1Record n1Row(String cnpj, String legalName, String product, String value, LocalDate visit) {
        return new N1Record(cnpj, legalName, product, 1, new BigDecimal(value),
                LocalDate.now().minusMonths(40), "Ativo", visit, null, null, "Rep", "(54) 90000-0000");
    }

    @Test
    void aggregatesN1ByCnpjSummingValueAndJoiningProducts() {
        List<N1Record> n1 = List.of(
                n1Row("12.345.678/0001-90", "Metalurgica Serra Azul", "Catraca", "1800", null),
                n1Row("12.345.678/0001-90", "Metalurgica Serra Azul", "CFTV", "2400", null)
        );
        List<Client360> cs = resolver.resolve(n1, List.of(), List.of(), List.of());
        assertEquals(1, cs.size(), "duas linhas do mesmo CNPJ viram um cliente");
        assertEquals(new BigDecimal("4200"), cs.get(0).totalValue());
        assertEquals(2, cs.get(0).products().size());
    }

    @Test
    void megazapServiceCountsAsConversation() {
        N1Record base = n1Row("23.456.789/0001-01", "Boa Vista", "CFTV", "1500", null);
        // A planilha do Megazap nao tem direcao: cada registro e uma conversa e conta como engajamento.
        List<MegazapRecord> mz = List.of(
                new MegazapRecord("23.456.789/0001-01", "Boa Vista", LocalDate.now().minusMonths(14)),
                new MegazapRecord("23.456.789/0001-01", "Boa Vista", LocalDate.now().minusDays(5))
        );
        Client360 c = resolver.resolve(List.of(base), mz, List.of(), List.of()).get(0);

        assertTrue(c.signals().stream().anyMatch(s -> s.source() == SignalSource.MEGAZAP),
                "atendimento no Megazap vira sinal de engajamento");
        assertEquals(LocalDate.now().minusDays(5), RecencyCalculator.lastInteraction(c.signals()).orElseThrow(),
                "a recencia usa o ultimo atendimento");
        assertEquals(MatchConfidence.EXATO, c.confidence());
    }

    @Test
    void npsWithoutCnpjMatchesByLegalNameAsUncertain() {
        N1Record base = n1Row("67.890.123/0001-45", "Gama Contabilidade LTDA", "Ponto", "1200", null);
        NpsRecord nps = new NpsRecord("", "GAMA CONTABILIDADE", 3, "comentario", LocalDate.now().minusMonths(8));

        Client360 c = resolver.resolve(List.of(base), List.of(), List.of(), List.of(nps)).get(0);

        assertEquals(MatchConfidence.INCERTO, c.confidence());
        assertEquals(3, c.npsScore());
    }

    @Test
    void peopleMatchesByCnpj() {
        N1Record base = n1Row("34.567.890/0001-12", "Delta", "Cancela", "800", null);
        PeopleRecord p = new PeopleRecord("34.567.890/0001-12", "Delta", "Cliente ativo", LocalDate.now().minusMonths(2));
        Client360 c = resolver.resolve(List.of(base), List.of(), List.of(p), List.of()).get(0);
        assertTrue(RecencyCalculator.lastInteraction(c.signals()).isPresent());
        assertEquals(LocalDate.now().minusMonths(2), RecencyCalculator.lastInteraction(c.signals()).orElseThrow());
    }
}
