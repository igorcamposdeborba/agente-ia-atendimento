package com.millenium.agente.integration;

import com.millenium.agente.adapter.excel.ExcelDataSource;
import com.millenium.agente.config.MilleniumProperties;
import com.millenium.agente.core.dto.Client360;
import com.millenium.agente.core.dto.InactiveClient;
import com.millenium.agente.core.dto.MatchConfidence;
import com.millenium.agente.core.service.ClientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Teste de INTEGRACAO ponta a ponta: le as CINCO planilhas ficticias reais (fixtures de teste em
 * {@code src/test/resources/fonte/*.xlsx}) atraves do adaptador Excel e do nucleo, exercitando
 * cruzamento + gatilhos + NPS de verdade (sem mocks).
 * <p>
 * As asserçoes escolhidas sao <b>independentes da data de hoje</b> (confianca por nome, cliente com
 * inatividade "infinita", visita muito antiga, notas de NPS fixas), para nao ficarem frageis com o
 * tempo.
 */
class PipelineIntegrationTest {

    private ClientService service;

    @BeforeEach
    void setUp() throws Exception {
        MilleniumProperties props = new MilleniumProperties();
        // Aponta a pasta de fontes para as fixtures do classpath (target/test-classes/fonte),
        // copiadas de src/test/resources/fonte. Assim o teste independe do que ha em
        // ~/MilleniumAgenteIA/fonte ou nos resources do jar principal.
        URL fixtures = getClass().getResource("/fonte");
        assertNotNull(fixtures, "fixtures ausentes: coloque os .xlsx em src/test/resources/fonte");
        props.setFonteDir(Path.of(fixtures.toURI()).toString());
        ExcelDataSource source = new ExcelDataSource(props);
        this.service = new ClientService(source, props);
    }

    private String names(List<InactiveClient> queue) {
        return queue.stream().map(InactiveClient::legalName).collect(Collectors.joining(" | "));
    }

    @Test
    void readsTheTenFixtureClients() {
        assertEquals(10, service.totalClients(), "as cinco planilhas ficticias trazem 10 clientes");
    }

    @Test
    void npsExactMatchIsExactAndNameMatchIsUncertain() {
        assertEquals(MatchConfidence.EXATO, service.find("PADARIA").orElseThrow().confidence());
        // casaram so por nome/fantasia (token distintivo) -> INCERTO -> conferencia humana
        assertEquals(MatchConfidence.INCERTO, service.find("LUCIANA ALVES").orElseThrow().confidence());
        assertEquals(MatchConfidence.INCERTO, service.find("VASCONCELOS").orElseThrow().confidence());
    }

    @Test
    void npsScoresAreReadFromColumnD() {
        assertEquals(8, service.find("SOUZA").orElseThrow().npsScore());
        // SPARRENBERGER nao tem resposta de NPS (o "Contabilidade Paim" ficou orfao)
        Client360 sparrenberger = service.find("SPARRENBERGER").orElseThrow();
        assertNull(sparrenberger.npsScore());
    }

    @Test
    void orphanNpsDoesNotBecomeAClient() {
        // "Contabilidade Paim" nao casa com seguranca -> nao vira cliente nem se cola em outro
        Optional<Client360> paim = service.find("CONTABILIDADE PAIM");
        assertTrue(paim.isEmpty(), "resposta de NPS sem casamento seguro nao entra na base");
    }

    @Test
    void triggerAContainsTheClientWithNoEngagement() {
        List<String> a = service.inactiveClients().stream().map(InactiveClient::legalName).toList();
        assertTrue(a.stream().anyMatch(n -> n.contains("SPARRENBERGER")),
                "cliente antigo sem nenhum engajamento entra no gatilho A. Fila A: " + a);
    }

    @Test
    void contactQueueIsUnionAndTriggerASubsetOfIt() {
        List<InactiveClient> union = service.clientsToContact(null);
        String u = names(union);
        assertTrue(u.contains("SPARRENBERGER"), "A∪B deve conter o inativo. Fila: " + u);
        assertTrue(u.contains("AUTOCENTER"), "A∪B deve conter quem tem preventiva vencida. Fila: " + u);

        Set<String> unionNames = union.stream().map(InactiveClient::legalName).collect(Collectors.toSet());
        for (InactiveClient a : service.inactiveClients()) {
            assertTrue(unionNames.contains(a.legalName()),
                    "todo cliente do gatilho A deve estar na fila A∪B: " + a.legalName());
        }
    }

    @Test
    void maintenanceOverdueQueueOnlyHasOverdueClients() {
        List<InactiveClient> onlyB = service.clientsToContact(true);
        assertFalse(onlyB.isEmpty(), "ha clientes com preventiva vencida na base ficticia");
        assertTrue(onlyB.stream().allMatch(InactiveClient::maintenanceOverdue),
                "a fila do gatilho B so traz clientes com preventiva vencida");
    }
}
