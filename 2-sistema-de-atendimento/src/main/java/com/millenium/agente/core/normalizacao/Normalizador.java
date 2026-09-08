package com.millenium.agente.core.normalizacao;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Normalizacao obrigatoria antes de qualquer join (design/cruzamento secao 8).
 * CNPJ -> so digitos; telefone -> formato unico com DDI; razao social -> caixa alta sem acento,
 * pontuacao ou sufixos societarios. Regras do field mapping (comentarios das celulas):
 * e-mail -> dominio (apos @ e antes do .), razao social do N1-NF/Sistema -> texto sem o "Numero-".
 */
public final class Normalizador {

    /** Termos genericos de ramo: nao bastam sozinhos para casar (evita "Contabilidade" -> "Sparrenberger"). */
    private static final Set<String> GENERICOS = Set.of(
            "CONTABILIDADE", "CONTABIL", "ASSESSORIA", "CONSULTORIA", "EMPRESARIAL", "ADVOCACIA",
            "ADVOGADOS", "RESTAURANTE", "MECANICA", "PADARIA", "CONFECCOES", "FOTOGRAFIA",
            "PRODUCAO", "AUDIOVISUAL", "ESTUDIO", "GASTROBAR", "AUTOCENTER", "COMERCIO",
            "SERVICOS", "TECNOLOGIA");

    private Normalizador() {
    }

    /** CNPJ apenas com digitos: "12.345.678/0001-90" -> "12345678000190". */
    public static String cnpj(String bruto) {
        if (bruto == null) return null;
        String digitos = bruto.replaceAll("\\D", "");
        return digitos.isEmpty() ? null : digitos;
    }

    /**
     * Telefone em formato unico "55DDNUMERO" (ex.: 5551999999999). Assume Brasil quando o DDI
     * nao vem. Remove tudo que nao e digito e prefixa 55 se necessario.
     */
    public static String telefone(String bruto) {
        if (bruto == null) return null;
        String d = bruto.replaceAll("\\D", "");
        if (d.isEmpty()) return null;
        if (d.startsWith("0")) d = d.replaceFirst("^0+", "");
        // 10 (fixo com DDD) ou 11 (celular com DDD) digitos -> prefixa DDI 55
        if (d.length() == 10 || d.length() == 11) {
            d = "55" + d;
        }
        return d;
    }

    /**
     * Razao social comparavel: caixa alta, sem acento, sem pontuacao, sem sufixos societarios
     * (LTDA, S.A., ME, EIRELI, EPP) e sem espacos duplicados.
     */
    public static String razaoSocial(String bruto) {
        if (bruto == null) return null;
        String semAcento = Normalizer.normalize(bruto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String upper = semAcento.toUpperCase()
                .replaceAll("[^A-Z0-9 ]", " ")
                .replaceAll("\\b(LTDA|SA|S A|ME|EIRELI|EPP|MEI)\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return upper.isEmpty() ? null : upper;
    }

    // ------------------------------------------------------------------
    // Regras do field mapping (comentarios das celulas) — adicionadas na
    // etapa "ajustar codigo conforme o planejamento".
    // ------------------------------------------------------------------

    /**
     * Remove o prefixo "Numero-" do nome do cliente no N1-NF (Pessoa) e N1-Sistema (Cliente):
     * "11111- PADARIA SILVA" ou "11111 - PADARIA SILVA" -> "PADARIA SILVA".
     * No N1-Contatos (Nome) nao ha prefixo e o texto volta inalterado.
     */
    public static String removerCodigo(String bruto) {
        if (bruto == null) return null;
        String r = bruto.replaceFirst("^\\s*\\d+\\s*-\\s*", "").trim();
        return r.isEmpty() ? null : r;
    }

    /** Codigo interno do cliente embutido no prefixo do nome do N1 ("11111- ...") -> "11111". */
    public static String codigoCliente(String bruto) {
        if (bruto == null) return null;
        var m = java.util.regex.Pattern.compile("^\\s*(\\d+)\\s*-").matcher(bruto);
        return m.find() ? m.group(1) : null;
    }

    /**
     * Dominio do e-mail (field mapping: "Extrair dominio apos o @ e antes do ."):
     * "financeiro@milleniumtec.com" -> "milleniumtec". A celula do CADASTRO e do PRODUTO pode
     * trazer uma LISTA separada por ";" -> retorna os dominios distintos, em ordem.
     */
    public static List<String> dominiosEmail(String bruto) {
        List<String> out = new ArrayList<>();
        if (bruto == null || bruto.isBlank()) return out;
        Set<String> vistos = new LinkedHashSet<>();
        for (String e : bruto.split(";")) {
            var m = java.util.regex.Pattern.compile("@([^.\\s@]+)").matcher(e.trim().toLowerCase());
            if (m.find()) vistos.add(m.group(1));
        }
        out.addAll(vistos);
        return out;
    }

    /** Tokens distintivos (sem acento, caixa alta, sem sufixos e sem termos genericos de ramo). */
    public static Set<String> tokensDistintivos(String bruto) {
        Set<String> toks = new LinkedHashSet<>();
        String r = razaoSocial(removerCodigo(bruto));
        if (r == null) return toks;
        for (String t : r.split(" ")) {
            if (!t.isBlank() && !GENERICOS.contains(t)) toks.add(t);
        }
        return toks;
    }

    /** Todos os tokens normalizados (inclui genericos). */
    public static Set<String> tokens(String bruto) {
        Set<String> toks = new LinkedHashSet<>();
        String r = razaoSocial(removerCodigo(bruto));
        if (r == null) return toks;
        for (String t : r.split(" ")) if (!t.isBlank()) toks.add(t);
        return toks;
    }
}
