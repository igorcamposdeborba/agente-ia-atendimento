package com.millenium.agente.adapter.excel;

import com.millenium.agente.config.MilleniumProperties;
import com.millenium.agente.core.model.RegistroMegazap;
import com.millenium.agente.core.model.RegistroN1;
import com.millenium.agente.core.model.RegistroNps;
import com.millenium.agente.core.model.RegistroPeople;
import com.millenium.agente.core.normalizacao.Normalizador;
import com.millenium.agente.core.port.FonteDadosPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Adaptador de saida da Fase 1 — le as CINCO planilhas do field mapping e aplica as regras dos
 * comentarios das celulas (design/cruzamento §3.1):
 * <ul>
 *   <li><b>N1-Contatos</b> (CADASTRO, prio 1): identidade — Nome, Fantasia, CnpjCpf, telefone, e-mail, Cod.</li>
 *   <li><b>N1-Saidas por NF</b> (NOTA_FISCAL, prio 2): <b>valor do contrato = soma de todas as NF</b>; data = ultima NF.</li>
 *   <li><b>N1-Sistema</b> (PRODUTO, prio 3): produtos (Servico, sem o "Numero-") e vigencias.</li>
 *   <li><b>Megazap</b> (WHATSAPP, prio 4): cada registro e uma <b>conversa</b> (conta como engajamento).</li>
 *   <li><b>NPS</b> (prio 5): nota pela <b>coluna D</b>, comentario pela <b>coluna E</b>; casa por nome fantasia.</li>
 * </ul>
 * O N1 e amarrado pelo <b>codigo do cliente</b> (Cod / prefixo "11111- ..."). O adaptador consolida
 * e entrega os registros que o nucleo (EntityResolver) ja consome — o Core NAO muda entre as fases.
 * <p>Regra invariavel preservada: so eventos originados no cliente viram engajamento; disparo de
 * marketing nao entra. O registro do Megazap e tratado como conversa (a planilha nao traz direcao).
 */
@Component
public class ExcelFonteDados implements FonteDadosPort {

    private static final Logger log = LoggerFactory.getLogger(ExcelFonteDados.class);

    private final MilleniumProperties props;
    private final PlanilhaLeitor leitor = new PlanilhaLeitor();

    private volatile List<RegistroN1> n1;
    private volatile List<RegistroMegazap> megazap;
    private volatile List<RegistroNps> nps;

    public ExcelFonteDados(MilleniumProperties props) {
        this.props = props;
    }

    // ---------- localizacao dos arquivos por trecho do nome (field mapping) ----------
    private Path arquivo(String... trechos) {
        Path dir = Path.of(props.getFonteDir());
        if (!Files.isDirectory(dir)) {
            log.warn("Pasta de dados nao encontrada: {}", dir.toAbsolutePath());
            return null;
        }
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> {
                        String n = p.getFileName().toString().toLowerCase();
                        return n.endsWith(".xlsx") && !n.startsWith("~$");
                    })
                    .filter(p -> {
                        String n = PlanilhaLeitor.norm(p.getFileName().toString());
                        for (String t : trechos) if (!n.contains(t)) return false;
                        return true;
                    })
                    .findFirst().orElse(null);
        } catch (IOException e) {
            log.warn("Falha ao listar {}: {}", dir, e.getMessage());
            return null;
        }
    }

    private static Map<String, Integer> indice(String[] header) {
        Map<String, Integer> idx = new LinkedHashMap<>();
        for (int i = 0; i < header.length; i++) idx.putIfAbsent(PlanilhaLeitor.norm(header[i]), i);
        return idx;
    }

    private static String cel(String[] row, Map<String, Integer> idx, String coluna) {
        Integer i = idx.get(coluna);
        if (i == null || i >= row.length) return null;
        String v = row[i];
        return (v == null || v.isBlank()) ? null : v.trim();
    }

    private static LocalDate maisRecente(LocalDate a, LocalDate b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isAfter(b) ? a : b;
    }

    private static LocalDate maisAntiga(LocalDate a, LocalDate b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isBefore(b) ? a : b;
    }

    // ============================ consolidacao ============================
    private synchronized void consolidar() {
        if (n1 != null) return;

        // --- N1-Contatos: identidade-mestre, indexada por codigo ---
        Map<String, Cliente> porCodigo = new LinkedHashMap<>();
        List<String[]> contatos = leitor.matriz(arquivo("contatos"));
        if (!contatos.isEmpty()) {
            Map<String, Integer> h = indice(contatos.get(0));
            for (int i = 1; i < contatos.size(); i++) {
                String[] r = contatos.get(i);
                String cod = cel(r, h, "cod");
                if (cod == null) continue;
                Cliente c = new Cliente();
                c.codigo = cod;
                c.razao = cel(r, h, "nome");
                c.fantasia = cel(r, h, "fantasia");
                c.cnpj = Normalizador.cnpj(cel(r, h, "cnpjcpf"));
                String tel = cel(r, h, "telefone");
                c.telefone = tel == null ? null : Normalizador.telefone(tel.split(";")[0]);
                c.emailDominios = Normalizador.dominiosEmail(cel(r, h, "email"));
                porCodigo.put(cod, c);
            }
        }
        log.info("N1-Contatos: {} cliente(s)", porCodigo.size());

        // --- N1-Sistema (PRODUTO): produtos + vigencias, por codigo ---
        List<String[]> sistema = leitor.matriz(arquivo("sistema"));
        if (!sistema.isEmpty()) {
            Map<String, Integer> h = indice(sistema.get(0));
            for (int i = 1; i < sistema.size(); i++) {
                String[] r = sistema.get(i);
                String cod = Normalizador.codigoCliente(cel(r, h, "cliente"));
                Cliente c = cod == null ? null : porCodigo.get(cod);
                if (c == null) continue;
                String serv = Normalizador.removerCodigo(cel(r, h, "servico"));
                if (serv != null && !c.produtos.contains(serv)) c.produtos.add(serv);
                LocalDate ini = PlanilhaLeitor.parseData(cel(r, h, "inicio vigencia"));
                LocalDate fim = PlanilhaLeitor.parseData(cel(r, h, "fim vigencia"));
                c.primeiroContrato = maisAntiga(c.primeiroContrato, ini);
                c.movimentoContrato = maisRecente(c.movimentoContrato, maisRecente(ini, fim));
            }
        }

        // --- N1-NF (NOTA_FISCAL): valor = SOMA de todas as NF; data = ultima NF ---
        List<String[]> nf = leitor.matriz(arquivo("saidas"));
        if (nf.isEmpty()) nf = leitor.matriz(arquivo("nf"));
        if (!nf.isEmpty()) {
            Map<String, Integer> h = indice(nf.get(0));
            for (int i = 1; i < nf.size(); i++) {
                String[] r = nf.get(i);
                String cod = Normalizador.codigoCliente(cel(r, h, "pessoa"));
                Cliente c = cod == null ? null : porCodigo.get(cod);
                if (c == null) continue;
                BigDecimal v = valor(cel(r, h, "valor total nf"));
                if (v != null) c.valorTotalNF = c.valorTotalNF.add(v);
                c.ultimaNF = maisRecente(c.ultimaNF, PlanilhaLeitor.parseData(cel(r, h, "data lancamento")));
            }
        }

        // --- monta os RegistroN1 (uma linha por produto; valor total na 1a; cnpj = chave do cliente) ---
        List<RegistroN1> outN1 = new ArrayList<>();
        for (Cliente c : porCodigo.values()) {
            String chave = c.cnpj != null ? c.cnpj : c.codigo;           // chave de agregacao do EntityResolver
            LocalDate visitaProxy = maisRecente(c.ultimaNF, c.movimentoContrato); // proxy de ultima visita
            List<String> prods = c.produtos.isEmpty() ? java.util.Collections.singletonList(null) : c.produtos;
            boolean primeira = true;
            for (String prod : prods) {
                outN1.add(new RegistroN1(
                        chave,
                        c.razao,
                        prod,
                        null,
                        primeira ? c.valorTotalNF : BigDecimal.ZERO,  // soma das NF na 1a linha; 0 nas demais
                        c.primeiroContrato,
                        "Ativo",
                        visitaProxy,                                  // ultimaVisita (proxy: ultima NF / vigencia)
                        c.ultimaNF,                                   // "atualizacao cadastral" = movimento de NF (compra)
                        c.movimentoContrato,                          // atualizacao/renovacao de contrato
                        null,                                         // representante (Contatos nao traz)
                        c.telefone));
                primeira = false;
            }
        }
        this.n1 = outN1;

        // --- Megazap (WHATSAPP): cada registro e uma conversa; casa por CNPJ ou telefone ---
        Map<String, String> clientePorCnpj = new LinkedHashMap<>();
        Map<String, String> clientePorTel = new LinkedHashMap<>();
        Map<String, Set<String>> dominioClientes = new LinkedHashMap<>();
        for (Cliente c : porCodigo.values()) {
            String chave = c.cnpj != null ? c.cnpj : c.codigo;
            if (c.cnpj != null) clientePorCnpj.put(c.cnpj, chave);
            if (c.telefone != null) clientePorTel.put(c.telefone, chave);
            for (String d : c.emailDominios) {
                dominioClientes.computeIfAbsent(d, k -> new java.util.LinkedHashSet<>()).add(chave);
            }
        }
        // o dominio do e-mail (field mapping) so vira chave auxiliar quando identifica UM unico
        // cliente — dominio compartilhado por varios (ex.: provedor comum) e ignorado.
        Map<String, String> clientePorDominio = new LinkedHashMap<>();
        for (var e : dominioClientes.entrySet()) {
            if (e.getValue().size() == 1) clientePorDominio.put(e.getKey(), e.getValue().iterator().next());
        }
        Map<String, LocalDate> zapMax = new LinkedHashMap<>();
        Map<String, String> zapRazao = new LinkedHashMap<>();
        List<String[]> zap = leitor.matriz(arquivo("megazap"));
        if (!zap.isEmpty()) {
            Map<String, Integer> h = indice(zap.get(0));
            for (int i = 1; i < zap.size(); i++) {
                String[] r = zap.get(i);
                LocalDate data = PlanilhaLeitor.parseData(cel(r, h, "data de criacao"));
                if (data == null) continue;   // pula a linha de legenda ("(Data de abertura...)")
                String cnpj = Normalizador.cnpj(cel(r, h, "cnpj"));
                String tel = cel(r, h, "telefone cliente");
                if (tel != null) tel = Normalizador.telefone(tel);
                java.util.List<String> doms = Normalizador.dominiosEmail(cel(r, h, "e mail"));
                String chave = null;
                if (cnpj != null && clientePorCnpj.containsKey(cnpj)) chave = clientePorCnpj.get(cnpj);
                else if (tel != null && clientePorTel.containsKey(tel)) chave = clientePorTel.get(tel);
                else if (!doms.isEmpty() && clientePorDominio.containsKey(doms.get(0))) chave = clientePorDominio.get(doms.get(0));
                if (chave == null) continue;   // chamado interno / sem casamento -> nao entra
                zapMax.merge(chave, data, ExcelFonteDados::maisRecenteNN);
                zapRazao.putIfAbsent(chave, cel(r, h, "empresa"));
            }
        }
        List<RegistroMegazap> outZap = new ArrayList<>();
        for (var e : zapMax.entrySet()) {
            outZap.add(new RegistroMegazap(e.getKey(), zapRazao.get(e.getKey()), e.getValue()));
        }
        this.megazap = outZap;

        // --- NPS: le por posicao (D=nota, E=comentario); casa por nome fantasia (pre-resolvido) ---
        List<RegistroNps> outNps = new ArrayList<>();
        Map<String, RegistroNps> npsPorCliente = new LinkedHashMap<>();
        List<String[]> npsM = leitor.matriz(arquivo("nps"));
        int orfaos = 0;
        if (!npsM.isEmpty()) {
            for (int i = 1; i < npsM.size(); i++) {
                String[] r = npsM.get(i);
                String empresa = r.length > 2 ? r[2] : null;                 // coluna C
                if (empresa == null || empresa.isBlank()) continue;
                LocalDate data = PlanilhaLeitor.parseData(r.length > 0 ? r[0] : null); // coluna A (carimbo)
                Integer nota = inteiro(r.length > 3 ? r[3] : null);           // coluna D
                String coment = r.length > 4 ? r[4] : null;                   // coluna E
                Cliente alvo = melhorCliente(empresa, porCodigo.values());
                if (alvo == null) { orfaos++; continue; }                     // sem casamento -> conferencia humana
                String chave = alvo.cnpj != null ? alvo.cnpj : alvo.codigo;
                boolean exato = casaExato(empresa, alvo);
                // exato -> casa por CNPJ (EXATO); incerto -> casa por razao social (INCERTO)
                RegistroNps novo = new RegistroNps(
                        exato ? chave : "",
                        alvo.razao,
                        nota, coment, data);
                RegistroNps atual = npsPorCliente.get(chave);
                if (atual == null || (data != null && (atual.data() == null || data.isAfter(atual.data())))) {
                    npsPorCliente.put(chave, novo);       // mantem o ultimo registro (data mais recente)
                }
            }
        }
        outNps.addAll(npsPorCliente.values());
        this.nps = outNps;
        if (orfaos > 0) log.info("NPS: {} resposta(s) sem casamento seguro -> conferencia humana", orfaos);
        log.info("Consolidado: {} cliente(s), {} linha(s) N1, {} Megazap, {} NPS",
                porCodigo.size(), outN1.size(), outZap.size(), outNps.size());
    }

    private static LocalDate maisRecenteNN(LocalDate a, LocalDate b) {
        return a == null ? b : (b == null ? a : (a.isAfter(b) ? a : b));
    }

    // casamento NPS (empresa) -> cliente por tokens distintivos (nome ∪ fantasia)
    private Cliente melhorCliente(String empresa, java.util.Collection<Cliente> clientes) {
        Set<String> alvo = Normalizador.tokensDistintivos(empresa);
        if (alvo.isEmpty()) return null;
        Cliente best = null;
        int bestScore = 0;
        for (Cliente c : clientes) {
            Set<String> cand = new java.util.LinkedHashSet<>(Normalizador.tokensDistintivos(c.razao));
            cand.addAll(Normalizador.tokensDistintivos(c.fantasia));
            int inter = 0;
            for (String t : alvo) if (cand.contains(t)) inter++;
            if (inter > bestScore) { bestScore = inter; best = c; }
        }
        return bestScore > 0 ? best : null;   // precisa de ao menos 1 token distintivo em comum
    }

    private boolean casaExato(String empresa, Cliente c) {
        Set<String> alvo = Normalizador.tokens(empresa);
        Set<String> cand = new java.util.LinkedHashSet<>(Normalizador.tokens(c.razao));
        cand.addAll(Normalizador.tokens(c.fantasia));
        return !alvo.isEmpty() && cand.containsAll(alvo);   // todos os tokens do NPS presentes
    }

    private static BigDecimal valor(String v) {
        if (v == null) return null;
        String s = v.replaceAll("[^0-9,.-]", "");
        int vv = s.lastIndexOf(','), vp = s.lastIndexOf('.');
        if (vv >= 0 && vp >= 0) s = (vv > vp) ? s.replace(".", "").replace(',', '.') : s.replace(",", "");
        else if (vv >= 0) s = s.replace(',', '.');
        try { return new BigDecimal(s); } catch (Exception e) { return null; }
    }

    private static Integer inteiro(String v) {
        if (v == null || v.isBlank()) return null;
        try { return Integer.valueOf(v.replaceAll("\\D", "")); } catch (Exception e) { return null; }
    }

    // ============================ FonteDadosPort ============================
    @Override public List<RegistroN1> lerN1()          { consolidar(); return n1; }
    @Override public List<RegistroMegazap> lerMegazap() { consolidar(); return megazap; }
    @Override public List<RegistroNps> lerNps()         { consolidar(); return nps; }
    @Override public List<RegistroPeople> lerPeople()   { return List.of(); }  // People fora do field mapping atual

    @Override
    public synchronized void recarregar() {
        log.info("Recarregando fontes Excel da pasta {}", props.getFonteDir());
        n1 = null; megazap = null; nps = null;
        consolidar();
    }

    /** Acumulador interno por cliente (chaveado pelo codigo do N1). */
    private static final class Cliente {
        String codigo, razao, fantasia, cnpj, telefone;
        List<String> emailDominios = new ArrayList<>();
        List<String> produtos = new ArrayList<>();
        BigDecimal valorTotalNF = BigDecimal.ZERO;
        LocalDate primeiroContrato, movimentoContrato, ultimaNF;
    }
}
