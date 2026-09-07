package com.millenium.agente.core.service;

import com.millenium.agente.config.MilleniumProperties;
import com.millenium.agente.core.model.ClienteInativo;
import com.millenium.agente.core.model.RegistroMegazap;
import com.millenium.agente.core.model.RegistroN1;
import com.millenium.agente.core.model.RegistroNps;
import com.millenium.agente.core.model.RegistroPeople;
import com.millenium.agente.core.port.FonteDadosPort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClienteServiceTest {

    private RegistroN1 n1(String cnpj, String razao, String valor, int inicioMeses, LocalDate visita) {
        return new RegistroN1(cnpj, razao, "CFTV", 1, new BigDecimal(valor),
                LocalDate.now().minusMonths(inicioMeses), "Ativo", visita, null, null, "Rep", "(54) 90000-0000");
    }

    private ClienteService servico(List<RegistroN1> n1, List<RegistroMegazap> mz) {
        FonteDadosPort fonte = new FonteDadosPort() {
            public List<RegistroN1> lerN1() { return n1; }
            public List<RegistroMegazap> lerMegazap() { return mz; }
            public List<RegistroPeople> lerPeople() { return List.of(); }
            public List<RegistroNps> lerNps() { return List.of(); }
            public void recarregar() { }
        };
        return new ClienteService(fonte, new MilleniumProperties());
    }

    @Test
    void gatilhoAApenasParaAntigosSemContatoRecente() {
        List<RegistroN1> base = List.of(
                n1("56.789.012/0001-34", "Farma", "2500", 72, null),                       // antigo, sem engajamento -> A
                n1("45.678.901/0001-23", "Elite", "500", 8, LocalDate.now().minusMonths(1)), // novo, visita recente -> fora
                n1("34.567.890/0001-12", "Delta", "800", 36, LocalDate.now().minusMonths(20)) // antigo mas com contato recente (megazap)
        );
        List<RegistroMegazap> mz = List.of(
                new RegistroMegazap("34.567.890/0001-12", "Delta", LocalDate.now().minusDays(2), "recebida")
        );
        ClienteService s = servico(base, mz);

        List<String> inativos = s.clientesInativos().stream().map(ClienteInativo::razaoSocial).toList();
        assertTrue(inativos.contains("Farma"));
        assertFalse(inativos.contains("Elite"), "cliente novo nao entra em inativos");
        assertFalse(inativos.contains("Delta"), "engajado recente nao entra no gatilho A");
    }

    @Test
    void gatilhoBPreventivaVencidaIndependeDaRecencia() {
        List<RegistroN1> base = List.of(
                n1("34.567.890/0001-12", "Delta", "800", 36, LocalDate.now().minusMonths(20)) // visita 20m -> preventiva vencida
        );
        List<RegistroMegazap> mz = List.of(
                new RegistroMegazap("34.567.890/0001-12", "Delta", LocalDate.now().minusDays(2), "recebida") // contato recente
        );
        ClienteService s = servico(base, mz);

        List<String> apenasB = s.clientesParaContato(true).stream().map(ClienteInativo::razaoSocial).toList();
        assertTrue(apenasB.contains("Delta"), "Delta entra por preventiva vencida mesmo com contato recente");

        // nao entra no gatilho A (contato recente)
        assertFalse(s.clientesInativos().stream().anyMatch(c -> c.razaoSocial().equals("Delta")));
    }
}
