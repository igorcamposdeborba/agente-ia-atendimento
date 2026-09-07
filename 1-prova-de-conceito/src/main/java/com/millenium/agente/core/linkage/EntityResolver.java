package com.millenium.agente.core.linkage;

import com.millenium.agente.core.model.Cliente360;
import com.millenium.agente.core.model.FonteSinal;
import com.millenium.agente.core.model.MatchConfianca;
import com.millenium.agente.core.model.RegistroMegazap;
import com.millenium.agente.core.model.RegistroN1;
import com.millenium.agente.core.model.RegistroNps;
import com.millenium.agente.core.model.RegistroPeople;
import com.millenium.agente.core.model.SinalEngajamento;
import com.millenium.agente.core.normalizacao.Normalizador;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cruzamento de registros. Como todas as fontes trazem CNPJ, o casamento e por <b>CNPJ (exato)</b>;
 * o NPS sem CNPJ cai para <b>razao social normalizada (incerto)</b> -> conferencia humana.
 * O N1 (uma linha por produto) e agregado por CNPJ. So sinais originados no cliente viram
 * {@link SinalEngajamento} (enviar != contato).
 */
public final class EntityResolver {

    public List<Cliente360> cruzar(List<RegistroN1> n1,
                                   List<RegistroMegazap> megazap,
                                   List<RegistroPeople> people,
                                   List<RegistroNps> nps) {

        // --- indices das fontes auxiliares (por CNPJ) ---
        Map<String, LocalDate> megazapRecebidaMax = new LinkedHashMap<>();
        for (RegistroMegazap m : megazap) {
            String cnpj = Normalizador.cnpj(m.cnpj());
            if (cnpj != null && m.recebida() && m.data() != null) {
                megazapRecebidaMax.merge(cnpj, m.data(), EntityResolver::maisRecente);
            }
        }
        Map<String, RegistroPeople> peoplePorCnpj = new LinkedHashMap<>();
        for (RegistroPeople p : people) {
            String cnpj = Normalizador.cnpj(p.cnpj());
            if (cnpj != null) peoplePorCnpj.putIfAbsent(cnpj, p);
        }
        Map<String, RegistroNps> npsPorCnpj = new LinkedHashMap<>();
        Map<String, RegistroNps> npsPorRazao = new LinkedHashMap<>();
        for (RegistroNps n : nps) {
            String cnpj = Normalizador.cnpj(n.cnpj());
            String razao = Normalizador.razaoSocial(n.razaoSocial());
            if (cnpj != null) npsPorCnpj.putIfAbsent(cnpj, n);
            if (razao != null) npsPorRazao.putIfAbsent(razao, n);
        }

        // --- agrega o N1 por CNPJ (uma linha por produto -> um cliente) ---
        Map<String, List<RegistroN1>> n1PorCnpj = new LinkedHashMap<>();
        for (RegistroN1 r : n1) {
            String cnpj = Normalizador.cnpj(r.cnpj());
            if (cnpj != null) n1PorCnpj.computeIfAbsent(cnpj, k -> new ArrayList<>()).add(r);
        }

        List<Cliente360> resultado = new ArrayList<>();
        for (var entrada : n1PorCnpj.entrySet()) {
            resultado.add(consolidar(entrada.getKey(), entrada.getValue(),
                    megazapRecebidaMax, peoplePorCnpj, npsPorCnpj, npsPorRazao));
        }
        return resultado;
    }

    private Cliente360 consolidar(String cnpj, List<RegistroN1> linhas,
                                  Map<String, LocalDate> megazapRecebidaMax,
                                  Map<String, RegistroPeople> peoplePorCnpj,
                                  Map<String, RegistroNps> npsPorCnpj,
                                  Map<String, RegistroNps> npsPorRazao) {

        RegistroN1 base = linhas.get(0);
        BigDecimal valorTotal = BigDecimal.ZERO;
        List<String> produtos = new ArrayList<>();
        LocalDate primeiroContrato = null, ultVisita = null, ultCadastral = null, ultContrato = null;
        for (RegistroN1 r : linhas) {
            if (r.valorMensal() != null) valorTotal = valorTotal.add(r.valorMensal());
            if (r.produto() != null) {
                produtos.add(r.quantidade() != null ? r.produto() + " (" + r.quantidade() + ")" : r.produto());
            }
            primeiroContrato = maisAntiga(primeiroContrato, r.primeiroContrato());
            ultVisita = maisRecente(ultVisita, r.ultimaVisita());
            ultCadastral = maisRecente(ultCadastral, r.ultimaAtualizacaoCadastral());
            ultContrato = maisRecente(ultContrato, r.ultimaAtualizacaoContrato());
        }

        List<SinalEngajamento> sinais = new ArrayList<>();
        List<String> fontesCasadas = new ArrayList<>();
        boolean incerto = false;

        addSinal(sinais, FonteSinal.N1, ultVisita, "visita registrada no N1");
        addSinal(sinais, FonteSinal.N1, ultCadastral, "atualização cadastral no N1");
        addSinal(sinais, FonteSinal.N1, ultContrato, "atualização de contrato no N1");

        LocalDate megazap = megazapRecebidaMax.get(cnpj);
        if (megazap != null) {
            addSinal(sinais, FonteSinal.MEGAZAP, megazap, "mensagem recebida no Megazap");
            fontesCasadas.add("Megazap por CNPJ");
        }

        RegistroPeople p = peoplePorCnpj.get(cnpj);
        if (p != null && p.ultimaInteracao() != null) {
            addSinal(sinais, FonteSinal.PEOPLE, p.ultimaInteracao(), "interação do cliente no People");
            String etapa = p.etapaFunil() != null ? " (funil: " + p.etapaFunil() + ")" : "";
            fontesCasadas.add("People por CNPJ" + etapa);
        }

        Integer npsNota = null;
        String npsComentario = null;
        LocalDate npsData = null;
        RegistroNps n = npsPorCnpj.get(cnpj);
        String chaveNps = "CNPJ";
        if (n == null) {
            String razao = Normalizador.razaoSocial(base.razaoSocial());
            if (razao != null) {
                n = npsPorRazao.get(razao);
                if (n != null) { chaveNps = "razão social"; incerto = true; }
            }
        }
        if (n != null) {
            npsNota = n.nota();
            npsComentario = n.comentario();
            npsData = n.data();
            if (n.data() != null) {
                addSinal(sinais, FonteSinal.NPS, n.data(), "resposta de NPS (" + n.faixa() + ")");
            }
            fontesCasadas.add("NPS por " + chaveNps);
        }

        return new Cliente360(
                cnpj,
                base.razaoSocial(),
                base.representante(),
                base.telefone(),
                valorTotal,
                primeiroContrato,
                base.statusContrato(),
                produtos,
                ultVisita,
                sinais,
                npsNota,
                npsComentario,
                npsData,
                incerto ? MatchConfianca.INCERTO : MatchConfianca.EXATO,
                fontesCasadas
        );
    }

    private static void addSinal(List<SinalEngajamento> sinais, FonteSinal fonte, LocalDate data, String desc) {
        if (data != null) sinais.add(new SinalEngajamento(fonte, data, desc));
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
}
