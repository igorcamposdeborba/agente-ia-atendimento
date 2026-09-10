package com.millenium.agente.core.normalizacao;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Normalizacao obrigatoria antes de qualquer join (design/cruzamento secao 8).
 * CNPJ -> so digitos; telefone -> formato unico com DDI; razao social -> caixa alta sem acento,
 * pontuacao ou sufixos societarios. Regras do field mapping (comentarios das celulas):
 * e-mail -> dominio (apos @ e antes do .), razao social do N1-NF/Sistema -> texto sem o "Numero-".
 * <p>
 * Obs.: usamos {@code java.text.Normalizer} totalmente qualificado para nao colidir com o nome
 * desta classe.
 */
public final class Normalizer {

    /** Termos genericos de ramo: nao bastam sozinhos para casar (evita "Contabilidade" -> "Sparrenberger"). */
    private static final Set<String> GENERIC_TERMS = Set.of(
            "CONTABILIDADE", "CONTABIL", "ASSESSORIA", "CONSULTORIA", "EMPRESARIAL", "ADVOCACIA",
            "ADVOGADOS", "RESTAURANTE", "MECANICA", "PADARIA", "CONFECCOES", "FOTOGRAFIA",
            "PRODUCAO", "AUDIOVISUAL", "ESTUDIO", "GASTROBAR", "AUTOCENTER", "COMERCIO",
            "SERVICOS", "TECNOLOGIA");

    private Normalizer() {
    }

    /** CNPJ apenas com digitos: "12.345.678/0001-90" -> "12345678000190". */
    public static String cnpj(String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("\\D", "");
        return digits.isEmpty() ? null : digits;
    }

    /**
     * Telefone em formato unico "55DDNUMERO" (ex.: 5551999999999). Assume Brasil quando o DDI
     * nao vem. Remove tudo que nao e digito e prefixa 55 se necessario.
     */
    public static String phone(String raw) {
        if (raw == null) return null;
        String d = raw.replaceAll("\\D", "");
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
    public static String legalName(String raw) {
        if (raw == null) return null;
        String noAccent = java.text.Normalizer.normalize(raw, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String upper = noAccent.toUpperCase()
                .replaceAll("[^A-Z0-9 ]", " ")
                .replaceAll("\\b(LTDA|SA|S A|ME|EIRELI|EPP|MEI)\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return upper.isEmpty() ? null : upper;
    }

    // ------------------------------------------------------------------
    // Regras do field mapping (comentarios das celulas).
    // ------------------------------------------------------------------

    /**
     * Remove o prefixo "Numero-" do nome do cliente no N1-NF (Pessoa) e N1-Sistema (Cliente):
     * "11111- PADARIA SILVA" ou "11111 - PADARIA SILVA" -> "PADARIA SILVA".
     * No N1-Contatos (Nome) nao ha prefixo e o texto volta inalterado.
     */
    public static String stripCode(String raw) {
        if (raw == null) return null;
        String r = raw.replaceFirst("^\\s*\\d+\\s*-\\s*", "").trim();
        return r.isEmpty() ? null : r;
    }

    /** Codigo interno do cliente embutido no prefixo do nome do N1 ("11111- ...") -> "11111". */
    public static String clientCode(String raw) {
        if (raw == null) return null;
        var m = java.util.regex.Pattern.compile("^\\s*(\\d+)\\s*-").matcher(raw);
        return m.find() ? m.group(1) : null;
    }

    /**
     * Dominio do e-mail (field mapping: "Extrair dominio apos o @ e antes do ."):
     * "financeiro@milleniumtec.com" -> "milleniumtec". A celula do CADASTRO e do PRODUTO pode
     * trazer uma LISTA separada por ";" -> retorna os dominios distintos, em ordem.
     */
    public static List<String> emailDomains(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) return out;
        Set<String> seen = new LinkedHashSet<>();
        for (String e : raw.split(";")) {
            var m = java.util.regex.Pattern.compile("@([^.\\s@]+)").matcher(e.trim().toLowerCase());
            if (m.find()) seen.add(m.group(1));
        }
        out.addAll(seen);
        return out;
    }

    /** Tokens distintivos (sem acento, caixa alta, sem sufixos e sem termos genericos de ramo). */
    public static Set<String> distinctiveTokens(String raw) {
        Set<String> toks = new LinkedHashSet<>();
        String r = legalName(stripCode(raw));
        if (r == null) return toks;
        for (String t : r.split(" ")) {
            if (!t.isBlank() && !GENERIC_TERMS.contains(t)) toks.add(t);
        }
        return toks;
    }

    /** Todos os tokens normalizados (inclui genericos). */
    public static Set<String> tokens(String raw) {
        Set<String> toks = new LinkedHashSet<>();
        String r = legalName(stripCode(raw));
        if (r == null) return toks;
        for (String t : r.split(" ")) if (!t.isBlank()) toks.add(t);
        return toks;
    }
}
