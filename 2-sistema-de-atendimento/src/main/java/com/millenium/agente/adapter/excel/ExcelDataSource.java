package com.millenium.agente.adapter.excel;

import com.millenium.agente.adapter.excel.SpreadsheetRows.ContactsRow;
import com.millenium.agente.adapter.excel.SpreadsheetRows.MegazapRow;
import com.millenium.agente.adapter.excel.SpreadsheetRows.InvoiceRow;
import com.millenium.agente.adapter.excel.SpreadsheetRows.NpsRow;
import com.millenium.agente.adapter.excel.SpreadsheetRows.SystemRow;
import com.millenium.agente.config.MilleniumProperties;
import com.millenium.agente.core.dto.MegazapRecord;
import com.millenium.agente.core.dto.N1Record;
import com.millenium.agente.core.dto.NpsRecord;
import com.millenium.agente.core.dto.PeopleRecord;
import com.millenium.agente.core.normalizacao.Normalizer;
import com.millenium.agente.core.port.DataSourcePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Adaptador de saida da Fase 1 - le as CINCO planilhas do field mapping, desserializa cada linha
 * num record tipado (via {@link ExcelSpreadsheet#byHeader}; o NPS por posicao) e consolida na visao
 * por cliente que o nucleo consome ({@link N1Record}/{@link MegazapRecord}/{@link NpsRecord}).
 * <p>
 * Regras do field mapping aplicadas aqui: N1 amarrado pelo codigo (Cod / prefixo "11111- ");
 * valor do contrato = soma de todas as NF; recencia por ultimo registro (NF/Megazap);
 * e-mail reduzido ao dominio (chave auxiliar); Megazap = conversa.
 */
@Component
public class ExcelDataSource implements DataSourcePort {

    private static final Logger log = LoggerFactory.getLogger(ExcelDataSource.class);

    private final MilleniumProperties props;
    private final ExcelSpreadsheet excel = new ExcelSpreadsheet();

    private volatile List<N1Record> n1;
    private volatile List<MegazapRecord> megazap;
    private volatile List<NpsRecord> nps;

    public ExcelDataSource(MilleniumProperties props) {
        this.props = props;
    }

    // ============================ DataSourcePort ============================
    @Override public List<N1Record> readN1()           { consolidate(); return n1; }
    @Override public List<MegazapRecord> readMegazap()  { consolidate(); return megazap; }
    @Override public List<NpsRecord> readNps()          { consolidate(); return nps; }
    @Override public List<PeopleRecord> readPeople()    { return List.of(); }  // People fora do field mapping atual

    @Override
    public synchronized void reload() {
        log.info("Recarregando fontes Excel da pasta {}", props.getFonteDir());
        n1 = null; megazap = null; nps = null;
        consolidate();
    }

    // ============================ consolidacao ============================
    private synchronized void consolidate() {
        if (n1 != null) return;
        seedSpreadsheets();   // 1a execucao: semeia a pasta de fontes se estiver vazia

        // 1) identidade-mestre (N1-Contatos), indexada por codigo
        Map<String, ClientAccumulator> byCode = new LinkedHashMap<>();
        for (ContactsRow l : excel.byHeader(file("contatos"), ContactsRow.class)) {
            if (l.code() == null || l.code().isBlank()) continue;
            byCode.put(l.code(), ClientAccumulator.from(l));
        }
        log.info("N1-Contatos: {} cliente(s)", byCode.size());

        // 2) contratos/vigencias (N1-Sistema), casados pelo codigo do prefixo do "Cliente"
        for (SystemRow l : excel.byHeader(file("sistema"), SystemRow.class)) {
            ClientAccumulator c = byCode.get(Normalizer.clientCode(l.client()));
            if (c == null) continue;
            c.addProduct(Normalizer.stripCode(l.service()));
            LocalDate start = ExcelSpreadsheet.parseDate(l.startValidity());
            LocalDate end = ExcelSpreadsheet.parseDate(l.endValidity());
            c.firstContract = earliest(c.firstContract, start);
            c.contractMovement = mostRecent(c.contractMovement, mostRecent(start, end));
        }

        // 3) notas fiscais (N1-NF): valor do contrato = SOMA das NF; recencia = ultima NF
        for (InvoiceRow l : excel.byHeader(invoiceFile(), InvoiceRow.class)) {
            ClientAccumulator c = byCode.get(Normalizer.clientCode(l.person()));
            if (c == null) continue;
            BigDecimal v = ExcelSpreadsheet.parseAmount(l.totalInvoiceValue());
            if (v != null) c.totalInvoiceValue = c.totalInvoiceValue.add(v);
            c.lastInvoice = mostRecent(c.lastInvoice, ExcelSpreadsheet.parseDate(l.postingDate()));
        }

        // 4) N1 consolidado -> registros que o EntityResolver agrega (chave = CNPJ real ou codigo)
        this.n1 = byCode.values().stream().flatMap(this::n1Records).toList();

        // 5) Megazap: cada registro e uma conversa; casa por CNPJ, telefone ou dominio de e-mail
        this.megazap = consolidateMegazap(byCode.values(),
                excel.byHeader(file("megazap"), MegazapRow.class));

        // 6) NPS: lido por posicao (A=carimbo, C=empresa, D=nota, E=comentario), pula o cabecalho
        //    (pergunta longa) e respostas sem empresa; casa por nome fantasia; ultimo registro por cliente
        List<NpsRow> npsRows = excel.positional(file("nps")).stream()
                .skip(1)
                .map(NpsRow::fromColumns)
                .filter(n -> n.company() != null && !n.company().isBlank())
                .toList();
        this.nps = consolidateNps(byCode.values(), npsRows);

        log.info("Consolidado: {} cliente(s), {} linha(s) N1, {} Megazap, {} NPS",
                byCode.size(), n1.size(), megazap.size(), nps.size());
    }

    /** Uma linha N1 por produto; o valor total das NF vai na primeira, 0 nas demais (o Core soma). */
    private Stream<N1Record> n1Records(ClientAccumulator c) {
        LocalDate visitProxy = mostRecent(c.lastInvoice, c.contractMovement);
        List<String> products = c.products.isEmpty() ? java.util.Collections.singletonList(null) : c.products;
        List<N1Record> rows = new ArrayList<>();
        for (int i = 0; i < products.size(); i++) {
            rows.add(new N1Record(
                    c.key(), c.legalName, products.get(i), null,
                    i == 0 ? c.totalInvoiceValue : BigDecimal.ZERO,   // soma das NF na 1a linha
                    c.firstContract, "Ativo",
                    visitProxy,                                       // ultima visita (proxy)
                    c.lastInvoice,                                    // compra registrada (NF)
                    c.contractMovement,                               // atualizacao/renovacao de contrato
                    null,                                             // representante (Contatos nao traz)
                    c.phone));
        }
        return rows.stream();
    }

    private List<MegazapRecord> consolidateMegazap(Collection<ClientAccumulator> clients, List<MegazapRow> rows) {
        Map<String, String> byCnpj = new LinkedHashMap<>();
        Map<String, String> byPhone = new LinkedHashMap<>();
        Map<String, Set<String>> byDomain = new LinkedHashMap<>();
        for (ClientAccumulator c : clients) {
            if (c.cnpj != null) byCnpj.put(c.cnpj, c.key());
            if (c.phone != null) byPhone.put(c.phone, c.key());
            c.emailDomains.forEach(d -> byDomain.computeIfAbsent(d, k -> new LinkedHashSet<>()).add(c.key()));
        }
        // dominio so identifica cliente quando e unico (dominio compartilhado por varios -> ignorado)
        Map<String, String> byUniqueDomain = new LinkedHashMap<>();
        byDomain.forEach((d, set) -> { if (set.size() == 1) byUniqueDomain.put(d, set.iterator().next()); });

        Map<String, LocalDate> lastByClient = new LinkedHashMap<>();
        Map<String, String> nameByClient = new LinkedHashMap<>();
        for (MegazapRow m : rows) {
            LocalDate date = ExcelSpreadsheet.parseDate(m.creationDate());
            if (date == null) continue;                              // pula a linha de legenda
            String cnpj = Normalizer.cnpj(m.cnpj());
            String phone = m.clientPhone() == null ? null : Normalizer.phone(m.clientPhone());
            List<String> doms = Normalizer.emailDomains(m.email());
            String key = cnpj != null && byCnpj.containsKey(cnpj) ? byCnpj.get(cnpj)
                    : phone != null && byPhone.containsKey(phone) ? byPhone.get(phone)
                    : !doms.isEmpty() && byUniqueDomain.containsKey(doms.get(0)) ? byUniqueDomain.get(doms.get(0))
                    : null;
            if (key == null) continue;                               // chamado interno / sem casamento
            lastByClient.merge(key, date, ExcelDataSource::mostRecentNN);
            nameByClient.putIfAbsent(key, m.company());
        }
        return lastByClient.entrySet().stream()
                .map(e -> new MegazapRecord(e.getKey(), nameByClient.get(e.getKey()), e.getValue()))
                .toList();
    }

    private List<NpsRecord> consolidateNps(Collection<ClientAccumulator> clients, List<NpsRow> rows) {
        Map<String, NpsRecord> lastByClient = new LinkedHashMap<>();
        int orphans = 0;
        for (NpsRow n : rows) {
            ClientAccumulator target = bestClient(n.company(), clients);
            if (target == null) { orphans++; continue; }             // sem casamento seguro -> conferencia humana
            LocalDate date = ExcelSpreadsheet.parseDate(n.timestamp());
            // exactMatch -> casa por CNPJ (EXATO); parcial -> casa por razao social (INCERTO)
            NpsRecord fresh = new NpsRecord(
                    exactMatch(n.company(), target) ? target.key() : "",
                    target.legalName,
                    ExcelSpreadsheet.parseInteger(n.score()),
                    n.comment(),
                    date);
            NpsRecord current = lastByClient.get(target.key());
            if (current == null || (date != null && (current.date() == null || date.isAfter(current.date())))) {
                lastByClient.put(target.key(), fresh);               // mantem o ultimo registro
            }
        }
        if (orphans > 0) log.info("NPS: {} resposta(s) sem casamento seguro -> conferencia humana", orphans);
        return List.copyOf(lastByClient.values());
    }

    // ---------- localizacao dos arquivos por trecho do nome (field mapping) ----------
    private Path file(String... parts) {
        Path dir = Path.of(props.getFonteDir());
        if (!Files.isDirectory(dir)) {
            log.warn("Pasta de dados não encontrada: {}", dir.toAbsolutePath());
            return null;
        }
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> {
                        String n = p.getFileName().toString().toLowerCase();
                        return n.endsWith(".xlsx") && !n.startsWith("~$");
                    })
                    .filter(p -> {
                        String n = ExcelSpreadsheet.norm(p.getFileName().toString());
                        for (String t : parts) if (!n.contains(t)) return false;
                        return true;
                    })
                    .findFirst().orElse(null);
        } catch (IOException e) {
            log.warn("Falha ao listar {}: {}", dir, e.getMessage());
            return null;
        }
    }

    /** A planilha de NF aparece como "...SAIDAS DE PRODUTOS POR NF..."; aceita "saidas" ou "nf". */
    private Path invoiceFile() {
        Path a = file("saidas");
        return a != null ? a : file("nf");
    }

    /**
     * Se a pasta de fontes ({@code millenium.fonte-dir}, padrao {@code ${user.home}/MilleniumAgenteIA/fonte})
     * estiver vazia, semeia as planilhas ficticias empacotadas no jar ({@code classpath:fonte/*.xlsx}).
     */
    private void seedSpreadsheets() {
        Path dir = Path.of(props.getFonteDir());
        try {
            if (Files.isDirectory(dir)) {
                try (Stream<Path> s = Files.list(dir)) {
                    if (s.anyMatch(p -> p.getFileName().toString().toLowerCase().endsWith(".xlsx"))) return;
                }
            }
            Files.createDirectories(dir);
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources("classpath*:fonte/*.xlsx");
            for (Resource r : resources) {
                String name = r.getFilename();
                if (name == null) continue;
                Path dest = dir.resolve(name);
                if (!Files.exists(dest)) {
                    try (var in = r.getInputStream()) {
                        Files.copy(in, dest);
                    }
                }
            }
            log.info("Pasta de fontes vazia - {} planilha(s) ficticia(s) semeada(s) em {}", resources.length, dir);
        } catch (Exception e) {
            log.warn("Nao foi possivel semear as planilhas em {}: {}", dir, e.getMessage());
        }
    }

    // ---------- casamento NPS por tokens do nome (empresa) x nome/fantasia do cliente ----------
    private ClientAccumulator bestClient(String company, Collection<ClientAccumulator> clients) {
        Set<String> target = Normalizer.distinctiveTokens(company);
        if (target.isEmpty()) return null;
        ClientAccumulator best = null;
        long bestScore = 0;
        for (ClientAccumulator c : clients) {
            long inter = target.stream().filter(c.distinctiveTokens()::contains).count();
            if (inter > bestScore) { bestScore = inter; best = c; }
        }
        return bestScore > 0 ? best : null;   // precisa de >= 1 token distintivo em comum
    }

    private boolean exactMatch(String company, ClientAccumulator c) {
        Set<String> target = Normalizer.tokens(company);
        return !target.isEmpty() && c.tokens().containsAll(target);   // todos os tokens do NPS presentes
    }

    // ---------- utilitarios de data ----------
    private static LocalDate mostRecent(LocalDate a, LocalDate b) {
        return a == null ? b : (b == null ? a : (a.isAfter(b) ? a : b));
    }

    private static LocalDate earliest(LocalDate a, LocalDate b) {
        return a == null ? b : (b == null ? a : (a.isBefore(b) ? a : b));
    }

    private static LocalDate mostRecentNN(LocalDate a, LocalDate b) {
        return mostRecent(a, b);
    }

    /** Acumulador por cliente (chaveado pelo codigo do N1). */
    private static final class ClientAccumulator {
        String code, legalName, tradeName, cnpj, phone;
        List<String> emailDomains = List.of();
        final List<String> products = new ArrayList<>();
        BigDecimal totalInvoiceValue = BigDecimal.ZERO;
        LocalDate firstContract, contractMovement, lastInvoice;

        static ClientAccumulator from(ContactsRow l) {
            ClientAccumulator c = new ClientAccumulator();
            c.code = l.code();
            c.legalName = l.legalName();
            c.tradeName = l.tradeName();
            c.cnpj = Normalizer.cnpj(l.cnpjCpf());
            c.phone = l.phone() == null ? null : Normalizer.phone(l.phone().split(";")[0]);
            c.emailDomains = Normalizer.emailDomains(l.email());
            return c;
        }

        /** Chave de agregacao do EntityResolver: CNPJ real quando existe, senao o codigo. */
        String key() {
            return cnpj != null ? cnpj : code;
        }

        void addProduct(String serv) {
            if (serv != null && !products.contains(serv)) products.add(serv);
        }

        Set<String> tokens() {
            Set<String> t = new LinkedHashSet<>(Normalizer.tokens(legalName));
            t.addAll(Normalizer.tokens(tradeName));
            return t;
        }

        Set<String> distinctiveTokens() {
            Set<String> t = new LinkedHashSet<>(Normalizer.distinctiveTokens(legalName));
            t.addAll(Normalizer.distinctiveTokens(tradeName));
            return t;
        }
    }
}
