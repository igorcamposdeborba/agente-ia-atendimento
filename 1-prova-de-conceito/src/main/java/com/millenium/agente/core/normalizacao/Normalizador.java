package com.millenium.agente.core.normalizacao;

import java.text.Normalizer;

/**
 * Normalizacao obrigatoria antes de qualquer join (complemento-v3 secao 3).
 * CNPJ -> so digitos; telefone -> formato unico com DDI; razao social -> caixa alta sem acento,
 * pontuacao ou sufixos societarios.
 */
public final class Normalizador {

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
}
