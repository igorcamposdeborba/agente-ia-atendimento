package com.millenium.agente.core.linkage;

import com.millenium.agente.core.dto.Client360;
import com.millenium.agente.core.dto.SignalSource;
import com.millenium.agente.core.dto.MatchConfidence;
import com.millenium.agente.core.dto.MegazapRecord;
import com.millenium.agente.core.dto.N1Record;
import com.millenium.agente.core.dto.NpsRecord;
import com.millenium.agente.core.dto.PeopleRecord;
import com.millenium.agente.core.dto.EngagementSignal;
import com.millenium.agente.core.normalizacao.Normalizer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cruzamento de registros. O adaptador ja entrega os registros consolidados por cliente (chave de
 * agregacao no campo {@code cnpj}: CNPJ real quando existe, senao o codigo interno do N1). O
 * casamento do NPS e por CNPJ (EXATO) ou razao social normalizada (INCERTO -> conferencia humana).
 * <p>
 * So sinais originados de uma interacao com o cliente viram {@link EngagementSignal}: resposta de
 * NPS, movimento no N1 (venda/vigencia/visita-proxy) e <b>atendimento no Megazap (conversa)</b>.
 * Disparo de marketing nunca vira sinal - {@code enviar != conversa}.
 */
public final class EntityResolver {

    public List<Client360> resolve(List<N1Record> n1,
                                   List<MegazapRecord> megazap,
                                   List<PeopleRecord> people,
                                   List<NpsRecord> nps) {

        // --- indices das fontes auxiliares (por CNPJ) ---
        // Cada registro do Megazap e uma conversa: contamos a data do ultimo atendimento por cliente.
        Map<String, LocalDate> megazapMax = new LinkedHashMap<>();
        for (MegazapRecord m : megazap) {
            String cnpj = Normalizer.cnpj(m.cnpj());
            if (cnpj != null && m.date() != null) {
                megazapMax.merge(cnpj, m.date(), EntityResolver::mostRecent);
            }
        }
        Map<String, PeopleRecord> peopleByCnpj = new LinkedHashMap<>();
        for (PeopleRecord p : people) {
            String cnpj = Normalizer.cnpj(p.cnpj());
            if (cnpj != null) peopleByCnpj.putIfAbsent(cnpj, p);
        }
        Map<String, NpsRecord> npsByCnpj = new LinkedHashMap<>();
        Map<String, NpsRecord> npsByName = new LinkedHashMap<>();
        for (NpsRecord n : nps) {
            String cnpj = Normalizer.cnpj(n.cnpj());
            String name = Normalizer.legalName(n.legalName());
            if (cnpj != null) npsByCnpj.putIfAbsent(cnpj, n);
            if (name != null) npsByName.putIfAbsent(name, n);
        }

        // --- agrega o N1 por chave (uma linha por produto -> um cliente) ---
        Map<String, List<N1Record>> n1ByKey = new LinkedHashMap<>();
        for (N1Record r : n1) {
            String key = Normalizer.cnpj(r.cnpj());
            if (key != null) n1ByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }

        List<Client360> result = new ArrayList<>();
        for (var entry : n1ByKey.entrySet()) {
            result.add(consolidate(entry.getKey(), entry.getValue(),
                    megazapMax, peopleByCnpj, npsByCnpj, npsByName));
        }
        return result;
    }

    private Client360 consolidate(String cnpj, List<N1Record> rows,
                                  Map<String, LocalDate> megazapMax,
                                  Map<String, PeopleRecord> peopleByCnpj,
                                  Map<String, NpsRecord> npsByCnpj,
                                  Map<String, NpsRecord> npsByName) {

        N1Record base = rows.get(0);
        BigDecimal totalValue = BigDecimal.ZERO;
        List<String> products = new ArrayList<>();
        LocalDate firstContract = null, lastVisit = null, lastRegistrationUpdate = null, lastContractUpdate = null;
        for (N1Record r : rows) {
            if (r.monthlyValue() != null) totalValue = totalValue.add(r.monthlyValue());
            if (r.product() != null) {
                products.add(r.quantity() != null ? r.product() + " (" + r.quantity() + ")" : r.product());
            }
            firstContract = earliest(firstContract, r.firstContract());
            lastVisit = mostRecent(lastVisit, r.lastVisit());
            lastRegistrationUpdate = mostRecent(lastRegistrationUpdate, r.lastRegistrationUpdate());
            lastContractUpdate = mostRecent(lastContractUpdate, r.lastContractUpdate());
        }

        List<EngagementSignal> signals = new ArrayList<>();
        List<String> matchedSources = new ArrayList<>();
        boolean uncertain = false;

        addSignal(signals, SignalSource.N1, lastVisit, "movimento no N1 (venda/visita - proxy de recencia)");
        addSignal(signals, SignalSource.N1, lastRegistrationUpdate, "compra registrada no N1 (nota fiscal)");
        addSignal(signals, SignalSource.N1, lastContractUpdate, "atualização/renovação de contrato no N1");

        LocalDate megazap = megazapMax.get(cnpj);
        if (megazap != null) {
            addSignal(signals, SignalSource.MEGAZAP, megazap, "atendimento registrado no Megazap (conversa)");
            matchedSources.add("Megazap por CNPJ/telefone");
        }

        PeopleRecord p = peopleByCnpj.get(cnpj);
        if (p != null && p.lastInteraction() != null) {
            addSignal(signals, SignalSource.PEOPLE, p.lastInteraction(), "interação do cliente no People");
            String stage = p.funnelStage() != null ? " (funil: " + p.funnelStage() + ")" : "";
            matchedSources.add("People por CNPJ" + stage);
        }

        Integer npsScore = null;
        String npsComment = null;
        LocalDate npsDate = null;
        NpsRecord n = npsByCnpj.get(cnpj);
        String npsKey = "CNPJ";
        if (n == null) {
            String name = Normalizer.legalName(base.legalName());
            if (name != null) {
                n = npsByName.get(name);
                if (n != null) { npsKey = "nome (fantasia/razão)"; uncertain = true; }
            }
        }
        if (n != null) {
            npsScore = n.score();
            npsComment = n.comment();
            npsDate = n.date();
            if (n.date() != null) {
                addSignal(signals, SignalSource.NPS, n.date(), "resposta de NPS (" + n.band() + ")");
            }
            matchedSources.add("NPS por " + npsKey);
        }

        return new Client360(
                cnpj,
                base.legalName(),
                base.representative(),
                base.phone(),
                totalValue,
                firstContract,
                base.contractStatus(),
                products,
                lastVisit,
                signals,
                npsScore,
                npsComment,
                npsDate,
                uncertain ? MatchConfidence.INCERTO : MatchConfidence.EXATO,
                matchedSources
        );
    }

    private static void addSignal(List<EngagementSignal> signals, SignalSource source, LocalDate date, String desc) {
        if (date != null) signals.add(new EngagementSignal(source, date, desc));
    }

    private static LocalDate mostRecent(LocalDate a, LocalDate b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isAfter(b) ? a : b;
    }

    private static LocalDate earliest(LocalDate a, LocalDate b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isBefore(b) ? a : b;
    }
}
