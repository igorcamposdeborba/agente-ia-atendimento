package com.millenium.agente.core.service;

import com.millenium.agente.config.MilleniumProperties;
import com.millenium.agente.core.linkage.EntityResolver;
import com.millenium.agente.core.dto.Client360;
import com.millenium.agente.core.dto.InactiveClient;
import com.millenium.agente.core.dto.MatchConfidence;
import com.millenium.agente.core.dto.Rfm;
import com.millenium.agente.core.dto.EngagementSignal;
import com.millenium.agente.core.normalizacao.Normalizer;
import com.millenium.agente.core.port.DataSourcePort;
import com.millenium.agente.core.recencia.RecencyCalculator;
import com.millenium.agente.core.rfm.RfmCalculator;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Nucleo de aplicacao: monta o Cliente 360 a partir das fontes, aplica recencia + RFM + os dois
 * gatilhos do Preventivo, e serve as consultas das tools do MCP.
 * <p>
 * Gatilho A (inatividade): cliente antigo E sem engajamento recente.
 * Gatilho B (preventiva vencida): sem visita ha >= janela (proxy, pois nao ha coluna de preventiva),
 * independente da recencia.
 */
@Service
public class ClientService {

    private final DataSourcePort source;
    private final MilleniumProperties props;
    private final EntityResolver resolver = new EntityResolver();
    private final RfmCalculator rfmCalculator;

    private volatile List<Client360> cache;

    public ClientService(DataSourcePort source, MilleniumProperties props) {
        this.source = source;
        this.props = props;
        this.rfmCalculator = new RfmCalculator(
                props.getPesoRecencia(), props.getPesoAntiguidade(), props.getPesoMonetizacao());
    }

    private List<Client360> clients() {
        List<Client360> local = cache;
        if (local == null) {
            synchronized (this) {
                if (cache == null) {
                    cache = resolver.resolve(source.readN1(), source.readMegazap(), source.readPeople(), source.readNps());
                }
                local = cache;
            }
        }
        return local;
    }

    public synchronized void reload() {
        source.reload();
        cache = null;
        clients();
    }

    public int totalClients() {
        return clients().size();
    }

    /** Visao consolidada (Cliente 360) de todos os clientes - usada pela exportacao em .xlsx. */
    public List<Client360> consolidatedClients() {
        return clients();
    }

    /**
     * Substitui os CNPJs mascarados (ex.: 12.***.***-90) pelos CNPJs completos e formatados.
     * Usado ao gerar o .docx (documento interno): o mascaramento vale so no canal MCP<->IA;
     * no arquivo de planejamento o CNPJ aparece completo. O CNPJ completo nunca vai ao modelo.
     */
    public String revealCnpjs(String text) {
        if (text == null) return null;
        String r = text;
        for (Client360 c : clients()) {
            if (c.cnpj() != null) {
                r = r.replace(c.maskedCnpj(), formatCnpj(c.cnpj()));
            }
        }
        return r;
    }

    /** 14 digitos -> 12.345.678/0001-90; caso contrario devolve como esta. */
    public static String formatCnpj(String digits) {
        if (digits == null) return null;
        String d = digits.replaceAll("\\D", "");
        if (d.length() != 14) return digits;
        return d.substring(0, 2) + "." + d.substring(2, 5) + "." + d.substring(5, 8)
                + "/" + d.substring(8, 12) + "-" + d.substring(12);
    }

    /** Busca por CNPJ (so digitos) ou razao social normalizada (contains). */
    public Optional<Client360> find(String term) {
        if (term == null || term.isBlank()) return Optional.empty();
        String cnpj = Normalizer.cnpj(term);
        String name = Normalizer.legalName(term);
        List<Client360> list = clients();
        if (cnpj != null && cnpj.length() >= 8) {
            Optional<Client360> byCnpj = list.stream().filter(c -> cnpj.equals(c.cnpj())).findFirst();
            if (byCnpj.isPresent()) return byCnpj;
        }
        if (name != null) {
            return list.stream()
                    .filter(c -> {
                        String r = Normalizer.legalName(c.legalName());
                        return r != null && r.contains(name);
                    })
                    .findFirst();
        }
        return Optional.empty();
    }

    public List<EngagementSignal> history(Client360 c) {
        List<EngagementSignal> ordered = new ArrayList<>(c.signals());
        ordered.sort(Comparator.comparing(EngagementSignal::date).reversed());
        return ordered;
    }

    /** Avalia um cliente: inatividade, antiguidade, gatilhos A/B e RFM. */
    public InactiveClient evaluate(Client360 c) {
        LocalDate today = LocalDate.now();
        Optional<LocalDate> last = RecencyCalculator.lastInteraction(c.signals());
        Long inactivity = RecencyCalculator.inactivityMonths(c.signals(), today).orElse(null);
        long tenure = c.firstContract() == null ? 0
                : ChronoUnit.MONTHS.between(c.firstContract(), today);

        boolean old = tenure >= props.getAntiguidadeLimiarMeses();
        boolean noRecentContact = inactivity == null || inactivity >= props.getInatividadeLimiarMeses();
        boolean triggerA = old && noRecentContact;

        boolean maintenanceOverdue = c.lastVisit() == null
                || ChronoUnit.MONTHS.between(c.lastVisit(), today) >= props.getPreventivaJanelaMeses();

        List<String> triggers = new ArrayList<>();
        if (triggerA) triggers.add("cliente antigo sem contato recente (inatividade)");
        if (maintenanceOverdue) {
            triggers.add("preventiva vencida [proxy: última visita "
                    + (c.lastVisit() == null ? "nunca registrada" : c.lastVisit()) + "]");
        }

        Rfm score = rfmCalculator.calculate(inactivity, tenure, c.totalValue());

        return new InactiveClient(
                c.maskedCnpj(),
                c.legalName(),
                c.representative(),
                c.phone(),
                c.totalValue(),
                c.products(),
                last.orElse(null),
                inactivity,
                tenure,
                triggerA,
                maintenanceOverdue,
                score,
                c.confidence(),
                triggers,
                buildEvidence(c),
                c.confidence() == MatchConfidence.INCERTO
        );
    }

    /** Gatilho A: clientes antigos sem contato recente, priorizados por RFM. */
    public List<InactiveClient> inactiveClients() {
        return sortByPriority(evaluateAll().stream().filter(InactiveClient::inactivityTrigger).toList());
    }

    /**
     * Fila de contato do Preventivo. Se {@code maintenanceOverdueOnly} = true, so o gatilho B; caso
     * contrario, a UNIAO A ou B (deduplicada por cliente), priorizada por RFM.
     */
    public List<InactiveClient> clientsToContact(Boolean maintenanceOverdueOnly) {
        List<InactiveClient> all = evaluateAll();
        List<InactiveClient> filtered = Boolean.TRUE.equals(maintenanceOverdueOnly)
                ? all.stream().filter(InactiveClient::maintenanceOverdue).toList()
                : all.stream().filter(ci -> ci.inactivityTrigger() || ci.maintenanceOverdue()).toList();
        return sortByPriority(filtered);
    }

    private List<InactiveClient> evaluateAll() {
        return clients().stream().map(this::evaluate).toList();
    }

    private List<InactiveClient> sortByPriority(List<InactiveClient> list) {
        return list.stream()
                .sorted(Comparator
                        .comparingDouble((InactiveClient ci) -> ci.rfm().score()).reversed()
                        .thenComparing(ci -> ci.totalValue() == null ? 0.0 : ci.totalValue().doubleValue(),
                                Comparator.reverseOrder()))
                .toList();
    }

    private List<String> buildEvidence(Client360 c) {
        List<String> ev = new ArrayList<>();
        history(c).forEach(s -> ev.add(s.date() + " - " + s.description() + " [" + s.source() + "]"));
        if (c.signals().isEmpty()) {
            ev.add("nenhum sinal de engajamento do cliente registrado nas fontes");
        }
        if (!c.products().isEmpty()) ev.add("produtos: " + String.join(", ", c.products()));
        if (c.npsScore() != null) {
            ev.add("NPS nota " + c.npsScore() + (c.npsComment() != null ? " - \"" + c.npsComment() + "\"" : ""));
        }
        ev.addAll(c.matchedSources());
        return ev;
    }
}
