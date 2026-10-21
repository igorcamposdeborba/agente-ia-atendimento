package com.millenium.agente.core.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Visao consolidada por cliente (o "Cliente 360"), montada em memoria a partir dos Excel.
 * Agrega as varias linhas do N1 (uma por produto) num unico cliente e pendura os sinais de
 * engajamento das demais fontes, com a marca de confianca do casamento.
 */
public record Client360(
        String cnpj,
        String legalName,
        String representative,
        String phone,
        BigDecimal totalValue,
        LocalDate firstContract,
        String status,
        List<String> products,
        LocalDate lastVisit,             // proxy da ultima preventiva (nao ha coluna propria)
        List<EngagementSignal> signals,
        Integer npsScore,
        String npsComment,
        LocalDate npsDate,
        Integer npsSecullumScore,
        String npsSecullumComment,
        LocalDate npsSecullumDate,
        MatchConfidence confidence,
        List<String> matchedSources
) {
    /** CNPJ mascarado por padrao (minimizacao); mostra 2 primeiros e 2 ultimos digitos. */
    public String maskedCnpj() {
        if (cnpj == null || cnpj.length() < 6) return "***";
        return cnpj.substring(0, 2) + ".***.***-" + cnpj.substring(cnpj.length() - 2);
    }

    /** Telefone mascarado por padrao (minimizacao): mostra so os 4 ultimos digitos. */
    public String maskedPhone() { return maskPhone(phone); }

    /** Mascara um telefone mantendo apenas os 4 ultimos digitos (preserva separadores). */
    public static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) return phone;
        long digits = phone.chars().filter(Character::isDigit).count();
        if (digits <= 4) return phone;
        StringBuilder sb = new StringBuilder();
        long seen = 0;
        for (char ch : phone.toCharArray()) {
            if (Character.isDigit(ch)) {
                seen++;
                sb.append(seen > digits - 4 ? ch : '*');
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }
}
