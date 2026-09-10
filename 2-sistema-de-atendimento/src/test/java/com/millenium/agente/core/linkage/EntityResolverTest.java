package com.millenium.agente.core.linkage;

import com.millenium.agente.core.dto.Cliente360;
import com.millenium.agente.core.dto.FonteSinal;
import com.millenium.agente.core.dto.MatchConfianca;
import com.millenium.agente.core.dto.RegistroMegazap;
import com.millenium.agente.core.dto.RegistroN1;
import com.millenium.agente.core.dto.RegistroNps;
import com.millenium.agente.core.dto.RegistroPeople;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityResolverTest {

    private final EntityResolver resolver = new EntityResolver();

    private RegistroN1 linhaN1(String cnpj, String razao, String produto, String valor,
                               LocalDate visita) {
        return new RegistroN1(cnpj, razao, produto, 1, new BigDecimal(valor),
                LocalDate.now().minusMonths(40), "Ativo", visita, null, null, "Rep", "(54) 90000-0000");
    }

    @Test
    void agregaN1PorCnpjSomandoValorEJuntandoProdutos() {
        List<RegistroN1> n1 = List.of(
                linhaN1("12.345.678/0001-90", "Metalurgica Serra Azul", "Catraca", "1800", null),
                linhaN1("12.345.678/0001-90", "Metalurgica Serra Azul", "CFTV", "2400", null)
        );
        List<Cliente360> cs = resolver.cruzar(n1, List.of(), List.of(), List.of());
        assertEquals(1, cs.size(), "duas linhas do mesmo CNPJ viram um cliente");
        assertEquals(new BigDecimal("4200"), cs.get(0).valorMensalTotal());
        assertEquals(2, cs.get(0).produtos().size());
    }

    @Test
    void atendimentoNoMegazapContaComoConversa() {
        RegistroN1 base = linhaN1("23.456.789/0001-01", "Boa Vista", "CFTV", "1500", null);
        // A planilha do Megazap nao tem direcao: cada registro e uma conversa e conta como engajamento.
        List<RegistroMegazap> mz = List.of(
                new RegistroMegazap("23.456.789/0001-01", "Boa Vista", LocalDate.now().minusMonths(14)),
                new RegistroMegazap("23.456.789/0001-01", "Boa Vista", LocalDate.now().minusDays(5))
        );
        Cliente360 c = resolver.cruzar(List.of(base), mz, List.of(), List.of()).get(0);

        assertTrue(c.sinais().stream().anyMatch(s -> s.fonte() == FonteSinal.MEGAZAP),
                "atendimento no Megazap vira sinal de engajamento");
        assertEquals(LocalDate.now().minusDays(5), c.ultimaInteracao().orElseThrow(),
                "a recencia usa o ultimo atendimento");
        assertEquals(MatchConfianca.EXATO, c.confianca());
    }

    @Test
    void npsSemCnpjCasaPorRazaoSocialComoIncerto() {
        RegistroN1 base = linhaN1("67.890.123/0001-45", "Gama Contabilidade LTDA", "Ponto", "1200", null);
        RegistroNps nps = new RegistroNps("", "GAMA CONTABILIDADE", 3, "comentario", LocalDate.now().minusMonths(8));

        Cliente360 c = resolver.cruzar(List.of(base), List.of(), List.of(), List.of(nps)).get(0);

        assertEquals(MatchConfianca.INCERTO, c.confianca());
        assertEquals(3, c.npsNota());
    }

    @Test
    void peopleEMegazapCasamPorCnpj() {
        RegistroN1 base = linhaN1("34.567.890/0001-12", "Delta", "Cancela", "800", null);
        RegistroPeople p = new RegistroPeople("34.567.890/0001-12", "Delta", "Cliente ativo", LocalDate.now().minusMonths(2));
        Cliente360 c = resolver.cruzar(List.of(base), List.of(), List.of(p), List.of()).get(0);
        assertTrue(c.ultimaInteracao().isPresent());
        assertEquals(LocalDate.now().minusMonths(2), c.ultimaInteracao().orElseThrow());
    }
}
