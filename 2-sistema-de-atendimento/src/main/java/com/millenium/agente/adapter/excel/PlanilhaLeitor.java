package com.millenium.agente.adapter.excel;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Leitor POI de baixo nivel: le a primeira aba de um .xlsx e devolve os valores ja como texto
 * previsivel (datas em ISO {@code yyyy-MM-dd}; inteiros sem o sufixo ".0"). Sobre este leitor:
 * <ul>
 *   <li>{@link #comCabecalho(Path)} — uma lista de mapas <b>cabecalho normalizado &rarr; valor</b>,
 *       consumida pelo {@link LeitorCabecalho} (que desserializa em records via Jackson);</li>
 *   <li>{@link #posicional(Path)} — as linhas cruas (por indice de coluna), usada pela
 *       especializacao {@link LeitorNps} (o NPS tem cabecalhos que sao perguntas longas).</li>
 * </ul>
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

    /** Linhas cruas (linha 0 = cabecalhos). Datas em ISO; inteiros sem ".0". */
    public List<String[]> posicional(Path arquivo) {
        if (arquivo == null || !Files.exists(arquivo)) {
            log.warn("Planilha nao encontrada: {} (retornando vazio)", arquivo);
            return List.of();
        }
        try (InputStream in = Files.newInputStream(arquivo);
             Workbook wb = new XSSFWorkbook(in)) {
            Sheet sheet = wb.getSheetAt(0);
            int firstRow = sheet.getFirstRowNum();
            Row header = sheet.getRow(firstRow);
            int lastCol = header == null ? 0 : header.getLastCellNum();
            List<String[]> linhas = new ArrayList<>();
            for (int i = firstRow; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                int nCols = Math.max(lastCol, row.getLastCellNum());
                String[] vals = new String[Math.max(nCols, 0)];
                boolean vazia = true;
                for (int c = 0; c < vals.length; c++) {
                    vals[c] = valorCelula(row.getCell(c));
                    if (!vals[c].isEmpty()) vazia = false;
                }
                if (!vazia) linhas.add(vals);
            }
            return linhas;
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao ler a planilha " + arquivo + ": " + e.getMessage(), e);
        }
    }

    /** Cada linha de dados como mapa {@code cabecalho normalizado -> valor} (cabecalho fora). */
    public List<Map<String, String>> comCabecalho(Path arquivo) {
        List<String[]> linhas = posicional(arquivo);
        if (linhas.isEmpty()) return List.of();
        String[] header = linhas.get(0);
        List<Map<String, String>> out = new ArrayList<>(linhas.size() - 1);
        for (int i = 1; i < linhas.size(); i++) {
            String[] row = linhas.get(i);
            Map<String, String> m = new LinkedHashMap<>();
            for (int c = 0; c < header.length; c++) {
                String v = c < row.length ? row[c] : "";
                if (!v.isEmpty()) m.put(norm(header[c]), v);   // vazios ficam de fora -> campo null no record
            }
            out.add(m);
        }
        return out;
    }

    private String valorCelula(Cell cell) {
        if (cell == null) return "";
        boolean numerico = cell.getCellType() == CellType.NUMERIC
                || (cell.getCellType() == CellType.FORMULA && cell.getCachedFormulaResultType() == CellType.NUMERIC);
        if (numerico) {
            if (DateUtil.isCellDateFormatted(cell)) {
                try {
                    return cell.getLocalDateTimeCellValue().toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
                } catch (Exception ignore) { /* cai para o formatador padrao */ }
            }
            double d = cell.getNumericCellValue();
            if (d == Math.floor(d) && !Double.isInfinite(d)) return String.valueOf((long) d);
            return BigDecimal.valueOf(d).stripTrailingZeros().toPlainString();
        }
        return formatter.formatCellValue(cell).trim();
    }

    /** Data robusta: aceita ISO (yyyy-MM-dd), "yyyy-MM-dd HH:mm[:ss]" e "dd/MM/yyyy". */
    public static LocalDate parseData(String v) {
        if (v == null || v.isBlank()) return null;
        String s = v.trim();
        try {
            if (s.length() >= 10 && s.charAt(4) == '-') return LocalDate.parse(s.substring(0, 10));
        } catch (Exception ignore) { /* tenta outros formatos */ }
        for (String pat : new String[]{"dd/MM/yyyy", "d/M/yyyy", "dd/MM/yy"}) {
            try {
                return LocalDate.parse(s.length() > 10 ? s.substring(0, 10) : s, DateTimeFormatter.ofPattern(pat));
            } catch (Exception ignore) { /* proximo */ }
        }
        log.warn("Data invalida: '{}'", v);
        return null;
    }

    /** Valor monetario BR/US -> BigDecimal (ou null). */
    public static BigDecimal parseValor(String v) {
        if (v == null || v.isBlank()) return null;
        String s = v.replaceAll("[^0-9,.-]", "");
        int vlgVirg = s.lastIndexOf(','), vlgPonto = s.lastIndexOf('.');
        if (vlgVirg >= 0 && vlgPonto >= 0) s = (vlgVirg > vlgPonto) ? s.replace(".", "").replace(',', '.') : s.replace(",", "");
        else if (vlgVirg >= 0) s = s.replace(',', '.');
        try { return new BigDecimal(s); } catch (Exception e) { return null; }
    }

    /** Inteiro tolerante (so digitos) -> Integer (ou null). */
    public static Integer parseInteiro(String v) {
        if (v == null || v.isBlank()) return null;
        try { return Integer.valueOf(v.replaceAll("\\D", "")); } catch (Exception e) { return null; }
    }
}
