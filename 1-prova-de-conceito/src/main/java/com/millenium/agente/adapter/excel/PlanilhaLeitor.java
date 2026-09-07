package com.millenium.agente.adapter.excel;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Le a primeira aba de um .xlsx em uma lista de mapas cabecalho -> valor (texto). Os cabecalhos sao
 * <b>normalizados</b> (sem acento, minusculo, pontuacao/espacos colapsados), o que torna a leitura
 * resiliente a variacoes como "razao social", "Razão Social", "últ. atualiz. cadastral".
 */
public class PlanilhaLeitor {

    private static final Logger log = LoggerFactory.getLogger(PlanilhaLeitor.class);
    private final DataFormatter formatter = new DataFormatter();

    /** Normaliza um cabecalho/coluna: sem acento, minusculo, so [a-z0-9] separados por espaco. */
    public static String norm(String s) {
        if (s == null) return "";
        String semAcento = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcento.toLowerCase().replaceAll("[^a-z0-9]+", " ").trim();
    }

    public List<Map<String, String>> ler(Path arquivo) {
        if (arquivo == null || !Files.exists(arquivo)) {
            log.warn("Planilha nao encontrada: {} (retornando vazio)", arquivo);
            return List.of();
        }
        try (InputStream in = Files.newInputStream(arquivo);
             Workbook wb = new XSSFWorkbook(in)) {
            return lerSheet(wb.getSheetAt(0));
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao ler a planilha " + arquivo + ": " + e.getMessage(), e);
        }
    }

    private List<Map<String, String>> lerSheet(Sheet sheet) {
        List<Map<String, String>> linhas = new ArrayList<>();
        Row header = sheet.getRow(sheet.getFirstRowNum());
        if (header == null) return linhas;

        List<String> colunas = new ArrayList<>();
        for (Cell c : header) {
            colunas.add(norm(formatter.formatCellValue(c)));
        }

        for (int i = sheet.getFirstRowNum() + 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            Map<String, String> linha = new LinkedHashMap<>();
            boolean vazia = true;
            for (int col = 0; col < colunas.size(); col++) {
                Cell cell = row.getCell(col);
                String valor = cell == null ? "" : formatter.formatCellValue(cell).trim();
                if (!valor.isEmpty()) vazia = false;
                linha.put(colunas.get(col), valor);
            }
            if (!vazia) linhas.add(linha);
        }
        return linhas;
    }

    // ---- conversores usados pelos adaptadores (buscam pela coluna normalizada) ----

    public static String texto(Map<String, String> linha, String coluna) {
        String v = linha.get(norm(coluna));
        return v == null || v.isBlank() ? null : v;
    }

    public static LocalDate data(Map<String, String> linha, String coluna) {
        String v = texto(linha, coluna);
        if (v == null) return null;
        try {
            return LocalDate.parse(v.trim());
        } catch (Exception e) {
            log.warn("Data invalida na coluna '{}': '{}'", coluna, v);
            return null;
        }
    }

    public static BigDecimal decimal(Map<String, String> linha, String coluna) {
        String v = texto(linha, coluna);
        if (v == null) return null;
        String s = v.replaceAll("[^0-9,.-]", "");
        int ultimaVirgula = s.lastIndexOf(',');
        int ultimoPonto = s.lastIndexOf('.');
        if (ultimaVirgula >= 0 && ultimoPonto >= 0) {
            if (ultimaVirgula > ultimoPonto) {
                s = s.replace(".", "").replace(',', '.'); // formato BR 1.234,56
            } else {
                s = s.replace(",", "");                    // formato US 1,234.56
            }
        } else if (ultimaVirgula >= 0) {
            s = s.replace(',', '.');                        // 1234,56
        }
        try {
            return new BigDecimal(s);
        } catch (Exception e) {
            log.warn("Valor invalido na coluna '{}': '{}'", coluna, v);
            return null;
        }
    }

    public static Integer inteiro(Map<String, String> linha, String coluna) {
        String v = texto(linha, coluna);
        if (v == null) return null;
        try {
            return Integer.valueOf(v.replaceAll("\\D", ""));
        } catch (Exception e) {
            return null;
        }
    }
}
