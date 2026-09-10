package com.millenium.agente.adapter.excel;

import com.millenium.agente.adapter.excel.LinhasPlanilha.LinhaContatos;
import com.millenium.agente.adapter.excel.LinhasPlanilha.LinhaMegazap;
import com.millenium.agente.adapter.excel.LinhasPlanilha.LinhaNf;
import com.millenium.agente.adapter.excel.LinhasPlanilha.LinhaNps;
import com.millenium.agente.adapter.excel.LinhasPlanilha.LinhaSistema;
import com.millenium.agente.config.MilleniumProperties;
import com.millenium.agente.core.dto.RegistroMegazap;
import com.millenium.agente.core.dto.RegistroN1;
import com.millenium.agente.core.dto.RegistroNps;
import com.millenium.agente.core.dto.RegistroPeople;
import com.millenium.agente.core.normalizacao.Normalizador;
import com.millenium.agente.core.port.FonteDadosPort;
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
 * Adaptador de saída da Fase 1 — lê as CINCO planilhas do field mapping, <b>desserializa</b> cada
 * linha num record tipado (via {@link PlanilhaExcel#porCabecalho}; o NPS por posição, pois seu
 * cabeçalho é uma pergunta longa) e <b>consolida</b> na visão por cliente que o núcleo consome
 * ({@link RegistroN1}/{@link RegistroMegazap}/{@link RegistroNps}). O Core NÃO muda entre as fases.
 * <p>
 * Regras do field mapping aplicadas aqui: N1 amarrado pelo <b>código</b> (Cod / prefixo "11111- ");
 * <b>valor do contrato = soma de todas as NF</b>; recência pelo <b>último registro</b> (NF/Megazap);
 * e-mail reduzido ao <b>domínio</b> (chave auxiliar); Megazap = <b>conversa</b> (sem "direção").
 */
@Component
public class ExcelFonteDados implements FonteDadosPort {

    private static final Logger log = LoggerFactory.getLogger(ExcelFonteDados.class);

    private final MilleniumProperties props;
    private final PlanilhaExcel excel = new PlanilhaExcel();

    private volatile List<RegistroN1> n1;
    private volatile List<RegistroMegazap> megazap;
    private volatile List<RegistroNps> nps;

    public ExcelFonteDados(MilleniumProperties props) {
        this.props = props;
    }

    // ============================ FonteDadosPort ============================
    @Override public List<RegistroN1> lerN1()            { consolidar(); return n1; }
    @Override public List<RegistroMegazap> lerMegazap()  { consolidar(); return megazap; }
    @Override public List<RegistroNps> lerNps()          { consolidar(); return nps; }
    @Override public List<RegistroPeople> lerPeople()    { return List.of(); }  // People fora do field mapping atual

    @Override
    public synchronized void recarregar() {
        log.info("Recarregando fontes Excel da pasta {}", props.getFonteDir());
        n1 = null; megazap = null; nps = null;
        consolidar();
    }

    // ============================ consolidação ============================
    private synchronized void consolidar() {
        if (n1 != null) return;
        garantirPlanilhas();   // 1a execução: semeia a pasta de fontes se estiver vazia

        // 1) identidade-mestre (N1-Contatos), indexada por código
        Map<String, Cliente> porCodigo = new LinkedHashMap<>();
        for (LinhaContatos l : excel.porCabecalho(arquivo("contatos"), LinhaContatos.class)) {
            if (l.codigo() == null || l.codigo().isBlank()) continue;
            porCodigo.put(l.codigo(), Cliente.de(l));
        }
        log.info("N1-Contatos: {} cliente(s)", porCodigo.size());

        // 2) contratos/vigências (N1-Sistema), casados pelo código do prefixo do "Cliente"
        for (LinhaSistema l : excel.porCabecalho(arquivo("sistema"), LinhaSistema.class)) {
            Cliente c = porCodigo.get(Normalizador.codigoCliente(l.cliente()));
            if (c == null) continue;
            c.adicionarProduto(Normalizador.removerCodigo(l.servico()));
            LocalDate ini = PlanilhaExcel.parseData(l.inicioVigencia());
            LocalDate fim = PlanilhaExcel.parseData(l.fimVigencia());
            c.primeiroContrato = maisAntiga(c.primeiroContrato, ini);
            c.movimentoContrato = maisRecente(c.movimentoContrato, maisRecente(ini, fim));
        }

        // 3) notas fiscais (N1-NF): valor do contrato = SOMA das NF; recência = última NF
        for (LinhaNf l : excel.porCabecalho(arquivoNf(), LinhaNf.class)) {
            Cliente c = porCodigo.get(Normalizador.codigoCliente(l.pessoa()));
            if (c == null) continue;
            BigDecimal v = PlanilhaExcel.parseValor(l.valorTotalNf());
            if (v != null) c.valorTotalNF = c.valorTotalNF.add(v);
            c.ultimaNF = maisRecente(c.ultimaNF, PlanilhaExcel.parseData(l.dataLancamento()));
        }

        // 4) N1 consolidado -> registros que o EntityResolver agrega (chave = CNPJ real ou código)
        this.n1 = porCodigo.values().stream().flatMap(this::registrosN1).toList();

        // 5) Megazap: cada registro é uma conversa; casa por CNPJ, telefone ou domínio de e-mail
        this.megazap = consolidarMegazap(porCodigo.values(),
                excel.porCabecalho(arquivo("megazap"), LinhaMegazap.class));

        // 6) NPS: lido por posição (A=carimbo, C=empresa, D=nota, E=comentário), pula o cabeçalho
        //    (pergunta longa) e respostas sem empresa; casa por nome fantasia; último registro por cliente
        List<LinhaNps> npsLinhas = excel.posicional(arquivo("nps")).stream()
                .skip(1)
                .map(LinhaNps::deColunas)
                .filter(n -> n.empresa() != null && !n.empresa().isBlank())
                .toList();
        this.nps = consolidarNps(porCodigo.values(), npsLinhas);

        log.info("Consolidado: {} cliente(s), {} linha(s) N1, {} Megazap, {} NPS",
                porCodigo.size(), n1.size(), megazap.size(), nps.size());
    }

    /** Uma linha N1 por produto; o valor total das NF vai na primeira, 0 nas demais (o Core soma). */
    private Stream<RegistroN1> registrosN1(Cliente c) {
        LocalDate visitaProxy = maisRecente(c.ultimaNF, c.movimentoContrato);
        List<String> produtos = c.produtos.isEmpty() ? java.util.Collections.singletonList(null) : c.produtos;
        List<RegistroN1> linhas = new ArrayList<>();
        for (int i = 0; i < produtos.size(); i++) {
            linhas.add(new RegistroN1(
                    c.chave(), c.razaoSocial, produtos.get(i), null,
                    i == 0 ? c.valorTotalNF : BigDecimal.ZERO,     // soma das NF na 1a linha
                    c.primeiroContrato, "Ativo",
                    visitaProxy,                                    // última visita (proxy)
                    c.ultimaNF,                                     // compra registrada (NF)
                    c.movimentoContrato,                            // atualização/renovação de contrato
                    null,                                           // representante (Contatos não traz)
                    c.telefone));
        }
        return linhas.stream();
    }

    private List<RegistroMegazap> consolidarMegazap(Collection<Cliente> clientes, List<LinhaMegazap> linhas) {
        Map<String, String> porCnpj = new LinkedHashMap<>();
        Map<String, String> porTel = new LinkedHashMap<>();
        Map<String, Set<String>> porDominio = new LinkedHashMap<>();
        for (Cliente c : clientes) {
            if (c.cnpj != null) porCnpj.put(c.cnpj, c.chave());
            if (c.telefone != null) porTel.put(c.telefone, c.chave());
            c.emailDominios.forEach(d -> porDominio.computeIfAbsent(d, k -> new LinkedHashSet<>()).add(c.chave()));
        }
        // domínio só identifica cliente quando é único (domínio compartilhado por vários -> ignorado)
        Map<String, String> porDominioUnico = new LinkedHashMap<>();
        porDominio.forEach((d, set) -> { if (set.size() == 1) porDominioUnico.put(d, set.iterator().next()); });

        Map<String, LocalDate> ultimoPorCliente = new LinkedHashMap<>();
        Map<String, String> razaoPorCliente = new LinkedHashMap<>();
        for (LinhaMegazap m : linhas) {
            LocalDate data = PlanilhaExcel.parseData(m.dataDeCriacao());
            if (data == null) continue;                              // pula a linha de legenda
            String cnpj = Normalizador.cnpj(m.cnpj());
            String tel = m.telefoneCliente() == null ? null : Normalizador.telefone(m.telefoneCliente());
            List<String> doms = Normalizador.dominiosEmail(m.email());
            String chave = cnpj != null && porCnpj.containsKey(cnpj) ? porCnpj.get(cnpj)
                    : tel != null && porTel.containsKey(tel) ? porTel.get(tel)
                    : !doms.isEmpty() && porDominioUnico.containsKey(doms.get(0)) ? porDominioUnico.get(doms.get(0))
                    : null;
            if (chave == null) continue;                             // chamado interno / sem casamento
            ultimoPorCliente.merge(chave, data, ExcelFonteDados::maisRecenteNN);
            razaoPorCliente.putIfAbsent(chave, m.empresa());
        }
        return ultimoPorCliente.entrySet().stream()
                .map(e -> new RegistroMegazap(e.getKey(), razaoPorCliente.get(e.getKey()), e.getValue()))
                .toList();
    }

    private List<RegistroNps> consolidarNps(Collection<Cliente> clientes, List<LinhaNps> linhas) {
        Map<String, RegistroNps> ultimoPorCliente = new LinkedHashMap<>();
        int orfaos = 0;
        for (LinhaNps n : linhas) {
            Cliente alvo = melhorCliente(n.empresa(), clientes);
            if (alvo == null) { orfaos++; continue; }                // sem casamento seguro -> conferência humana
            LocalDate data = PlanilhaExcel.parseData(n.carimbo());
            // casaExato -> casa por CNPJ (EXATO); parcial -> casa por razão social (INCERTO)
            RegistroNps novo = new RegistroNps(
                    casaExato(n.empresa(), alvo) ? alvo.chave() : "",
                    alvo.razaoSocial,
                    PlanilhaExcel.parseInteiro(n.nota()),
                    n.comentario(),
                    data);
            RegistroNps atual = ultimoPorCliente.get(alvo.chave());
            if (atual == null || (data != null && (atual.data() == null || data.isAfter(atual.data())))) {
                ultimoPorCliente.put(alvo.chave(), novo);            // mantém o último registro
            }
        }
        if (orfaos > 0) log.info("NPS: {} resposta(s) sem casamento seguro -> conferência humana", orfaos);
        return List.copyOf(ultimoPorCliente.values());
    }

    // ---------- localização dos arquivos por trecho do nome (field mapping) ----------
    private Path arquivo(String... trechos) {
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
                        String n = PlanilhaExcel.norm(p.getFileName().toString());
                        for (String t : trechos) if (!n.contains(t)) return false;
                        return true;
                    })
                    .findFirst().orElse(null);
        } catch (IOException e) {
            log.warn("Falha ao listar {}: {}", dir, e.getMessage());
            return null;
        }
    }

    /** A planilha de NF aparece como "...SAÍDAS DE PRODUTOS POR NF..."; aceita "saidas" ou "nf". */
    private Path arquivoNf() {
        Path a = arquivo("saidas");
        return a != null ? a : arquivo("nf");
    }

    /**
     * Se a pasta de fontes ({@code millenium.fonte-dir}, padrão {@code ${user.home}/MilleniumAgenteIA/fonte})
     * estiver vazia, semeia as planilhas fictícias empacotadas no jar ({@code classpath:fonte/*.xlsx}).
     * Assim o .mcpb funciona de imediato lendo sempre do mesmo caminho, sem depender de arquivos
     * dentro da extensão. Para dados reais, basta substituir os .xlsx nessa pasta.
     */
    private void garantirPlanilhas() {
        Path dir = Path.of(props.getFonteDir());
        try {
            if (Files.isDirectory(dir)) {
                try (Stream<Path> s = Files.list(dir)) {
                    if (s.anyMatch(p -> p.getFileName().toString().toLowerCase().endsWith(".xlsx"))) return;
                }
            }
            Files.createDirectories(dir);
            Resource[] recursos = new PathMatchingResourcePatternResolver().getResources("classpath*:fonte/*.xlsx");
            for (Resource r : recursos) {
                String nome = r.getFilename();
                if (nome == null) continue;
                Path destino = dir.resolve(nome);
                if (!Files.exists(destino)) {
                    try (var in = r.getInputStream()) {
                        Files.copy(in, destino);
                    }
                }
            }
            log.info("Pasta de fontes vazia — {} planilha(s) fictícia(s) semeada(s) em {}", recursos.length, dir);
        } catch (Exception e) {
            log.warn("Não foi possível semear as planilhas em {}: {}", dir, e.getMessage());
        }
    }

    // ---------- casamento NPS por tokens do nome (empresa) x nome/fantasia do cliente ----------
    private Cliente melhorCliente(String empresa, Collection<Cliente> clientes) {
        Set<String> alvo = Normalizador.tokensDistintivos(empresa);
        if (alvo.isEmpty()) return null;
        Cliente melhor = null;
        long melhorScore = 0;
        for (Cliente c : clientes) {
            long inter = alvo.stream().filter(c.tokensDistintivos()::contains).count();
            if (inter > melhorScore) { melhorScore = inter; melhor = c; }
        }
        return melhorScore > 0 ? melhor : null;   // precisa de >= 1 token distintivo em comum
    }

    private boolean casaExato(String empresa, Cliente c) {
        Set<String> alvo = Normalizador.tokens(empresa);
        return !alvo.isEmpty() && c.tokens().containsAll(alvo);   // todos os tokens do NPS presentes
    }

    // ---------- utilitários de data ----------
    private static LocalDate maisRecente(LocalDate a, LocalDate b) {
        return a == null ? b : (b == null ? a : (a.isAfter(b) ? a : b));
    }

    private static LocalDate maisAntiga(LocalDate a, LocalDate b) {
        return a == null ? b : (b == null ? a : (a.isBefore(b) ? a : b));
    }

    private static LocalDate maisRecenteNN(LocalDate a, LocalDate b) {
        return maisRecente(a, b);
    }

    /** Acumulador por cliente (chaveado pelo código do N1). */
    private static final class Cliente {
        String codigo, razaoSocial, nomeFantasia, cnpj, telefone;
        List<String> emailDominios = List.of();
        final List<String> produtos = new ArrayList<>();
        BigDecimal valorTotalNF = BigDecimal.ZERO;
        LocalDate primeiroContrato, movimentoContrato, ultimaNF;

        static Cliente de(LinhaContatos l) {
            Cliente c = new Cliente();
            c.codigo = l.codigo();
            c.razaoSocial = l.razaoSocial();
            c.nomeFantasia = l.nomeFantasia();
            c.cnpj = Normalizador.cnpj(l.cnpjCpf());
            c.telefone = l.telefone() == null ? null : Normalizador.telefone(l.telefone().split(";")[0]);
            c.emailDominios = Normalizador.dominiosEmail(l.email());
            return c;
        }

        /** Chave de agregação do EntityResolver: CNPJ real quando existe, senão o código. */
        String chave() {
            return cnpj != null ? cnpj : codigo;
        }

        void adicionarProduto(String serv) {
            if (serv != null && !produtos.contains(serv)) produtos.add(serv);
        }

        Set<String> tokens() {
            Set<String> t = new LinkedHashSet<>(Normalizador.tokens(razaoSocial));
            t.addAll(Normalizador.tokens(nomeFantasia));
            return t;
        }

        Set<String> tokensDistintivos() {
            Set<String> t = new LinkedHashSet<>(Normalizador.tokensDistintivos(razaoSocial));
            t.addAll(Normalizador.tokensDistintivos(nomeFantasia));
            return t;
        }
    }
}
