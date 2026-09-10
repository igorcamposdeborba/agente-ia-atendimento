package com.millenium.agente.core.service;

import com.millenium.agente.config.MilleniumProperties;
import com.millenium.agente.core.dto.InactiveClient;
import com.millenium.agente.core.dto.MegazapRecord;
import com.millenium.agente.core.dto.N1Record;
import com.millenium.agente.core.dto.NpsRecord;
import com.millenium.agente.core.dto.PeopleRecord;
import com.millenium.agente.core.port.DataSourcePort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Teste de componente dos dois gatilhos do Preventivo (A inatividade / B preventiva vencida) e da
 * fila, com uma fonte de dados em memoria (sem Excel).
 */
class ClientServiceTest {

    private N1Record n1(String cnpj, String legalName, String value, int startMonths, LocalDate visit) {
        return new N1Record(cnpj, legalName, "CFTV", 1, new BigDecimal(value),
                LocalDate.now().minusMonths(startMonths), "Ativo", visit, null, null, "Rep", "(54) 90000-0000");
    }

    private ClientService service(List<N1Record> n1, List<MegazapRecord> mz) {
        DataSourcePort source = new DataSourcePort() {
            public List<N1Record> readN1() { return n1; }
            public List<MegazapRecord> readMegazap() { return mz; }
            public List<PeopleRecord> readPeople() { return List.of(); }
            public List<NpsRecord> readNps() { return List.of(); }
            public void reload() { }
        };
        return new ClientService(source, new MilleniumProperties());
    }

    @Test
    void triggerAOnlyForOldClientsWithoutRecentContact() {
        List<N1Record> base = List.of(
                n1("56.789.012/0001-34", "Farma", "2500", 72, null),                        // antigo, sem engajamento -> A
                n1("45.678.901/0001-23", "Elite", "500", 8, LocalDate.now().minusMonths(1)), // novo, visita recente -> fora
                n1("34.567.890/0001-12", "Delta", "800", 36, LocalDate.now().minusMonths(20)) // antigo mas com contato recente (megazap)
        );
        List<MegazapRecord> mz = List.of(
                new MegazapRecord("34.567.890/0001-12", "Delta", LocalDate.now().minusDays(2))
        );
        ClientService s = service(base, mz);

        List<String> inactive = s.inactiveClients().stream().map(InactiveClient::legalName).toList();
        assertTrue(inactive.contains("Farma"));
        assertFalse(inactive.contains("Elite"), "cliente novo nao entra em inativos");
        assertFalse(inactive.contains("Delta"), "engajado recente (atendimento no Megazap) nao entra no gatilho A");
    }

    @Test
    void triggerBMaintenanceOverdueIsIndependentOfRecency() {
        List<N1Record> base = List.of(
                n1("34.567.890/0001-12", "Delta", "800", 36, LocalDate.now().minusMonths(20)) // visita 20m -> preventiva vencida
        );
        List<MegazapRecord> mz = List.of(
                new MegazapRecord("34.567.890/0001-12", "Delta", LocalDate.now().minusDays(2)) // conversa recente
        );
        ClientService s = service(base, mz);

        List<String> onlyB = s.clientsToContact(true).stream().map(InactiveClient::legalName).toList();
        assertTrue(onlyB.contains("Delta"), "Delta entra por preventiva vencida mesmo com contato recente");

        // nao entra no gatilho A (contato recente)
        assertFalse(s.inactiveClients().stream().anyMatch(c -> c.legalName().equals("Delta")));
    }

    @Test
    void contactQueueIsTheDeduplicatedUnionOfAandB() {
        List<N1Record> base = List.of(
                n1("56.789.012/0001-34", "Farma", "2500", 72, LocalDate.now().minusMonths(30)) // A (sem engajamento) e B (visita 30m)
        );
        ClientService s = service(base, List.of());
        // um cliente que dispara A e B aparece UMA vez
        assertTrue(s.clientsToContact(null).stream().filter(c -> c.legalName().equals("Farma")).count() == 1,
                "A fila deduplica: um contato leva os dois assuntos");
        InactiveClient farma = s.clientsToContact(null).get(0);
        assertTrue(farma.inactivityTrigger() && farma.maintenanceOverdue(), "Farma dispara A e B");
    }
}
