package com.millenium.agente.adapter.excel;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
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
 * I/O de planilhas .xlsx via Apache POI, num unico lugar. Leitura crua ({@link #positional}),
 * leitura tipada por cabecalho com Jackson ({@link #byHeader}), conversoes ({@link #parseDate},
 * {@link #parseAmount}, {@link #parseInteger}, {@link #norm}) e escrita de tabela ({@link #write}).
 */
public final class ExcelSpreadsheet {

    private static final Logger log = LoggerFactory.getLogger(ExcelSpreadsheet.class);
    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final DataFormatter formatter = new DataFormatter();

    // ============================ Leitura ============================

    /** Linhas cruas (linha 0 = cabecalhos). Datas em ISO; inteiros sem ".0". Vazio se o arquivo falta. */
    public List<String[]> positional(Path file) {
        if (file == null || !Files.exists(file)) {
            log.warn("Planilha não encontrada: {} (retornando vazio)", file);
            return List.of();
        }
        try (InputStream in = Files.newInputStream(file);
             Workbook wb = new XSSFWorkbook(in)) {
            Sheet sheet = wb.getSheetAt(0);
            int firstRow = sheet.getFirstRowNum();
            Row header = sheet.getRow(firstRow);
            int lastCol = header == null ? 0 : header.getLastCellNum();
            List<String[]> rows = new ArrayList<>();
            for (int i = firstRow; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                int nCols = Math.max(lastCol, row.getLastCellNum());
                String[] vals = new String[Math.max(nCols, 0)];
                boolean empty = true;
                for (int c = 0; c < vals.length; c++) {
                    vals[c] = cellValue(row.getCell(c));
                    if (!vals[c].isEmpty()) empty = false;
                }
                if (!empty) rows.add(vals);
            }
            return rows;
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao ler a planilha " + file + ": " + e.getMessage(), e);
        }
    }

    /**
     * Cada linha de dados desserializada no record {@code T} pelo Jackson: monta o mapa
     * {@code cabecalho normalizado -> valor} (celulas vazias ficam de fora, virando campo {@code null})
     * e converte. Leitura por cabecalho num passo so.
     */
    public <T> List<T> byHeader(Path file, Class<T> type) {
        List<String[]> rows = positional(file);
        if (rows.isEmpty()) return List.of();
        String[] header = rows.get(0);
        List<T> out = new ArrayList<>(rows.size() - 1);
        for (int i = 1; i < rows.size(); i++) {
            String[] row = rows.get(i);
            Map<String, String> m = new LinkedHashMap<>();
            for (int c = 0; c < header.length; c++) {
                String v = c < row.length ? row[c] : "";
                if (!v.isEmpty()) m.put(norm(header[c]), v);
            }
            out.add(JSON.convertValue(m, type));
        }
        return out;
    }

    private String cellValue(Cell cell) {
        if (cell == null) return "";
        boolean numeric = cell.getCellType() == CellType.NUMERIC
                || (cell.getCellType() == CellType.FORMULA && cell.getCachedFormulaResultType() == CellType.NUMERIC);
        if (numeric) {
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

    // ============================ Conversoes ============================

    /** Normaliza um cabecalho/coluna: sem acento, minusculo, so [a-z0-9] separados por espaco. */
    public static String norm(String s) {
        if (s == null) return "";
        String noAccent = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return noAccent.toLowerCase().replaceAll("[^a-z0-9]+", " ").trim();
    }

    /** Data robusta: aceita ISO (yyyy-MM-dd), "yyyy-MM-dd HH:mm[:ss]" e "dd/MM/yyyy". */
    public static LocalDate parseDate(String v) {
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
        log.warn("Data inválida: '{}'", v);
        return null;
    }

    /** Valor monetario BR/US -> BigDecimal (ou null). */
    public static BigDecimal parseAmount(String v) {
        if (v == null || v.isBlank()) return null;
        String s = v.replaceAll("[^0-9,.-]", "");
        int lastComma = s.lastIndexOf(','), lastDot = s.lastIndexOf('.');
        if (lastComma >= 0 && lastDot >= 0) s = (lastComma > lastDot) ? s.replace(".", "").replace(',', '.') : s.replace(",", "");
        else if (lastComma >= 0) s = s.replace(',', '.');
        try { return new BigDecimal(s); } catch (Exception e) { return null; }
    }

    /** Inteiro tolerante (so digitos) -> Integer (ou null). */
    public static Integer parseInteger(String v) {
        if (v == null || v.isBlank()) return null;
        try { return Integer.valueOf(v.replaceAll("\\D", "")); } catch (Exception e) { return null; }
    }

    // ============================ Escrita ============================

    /** Grava uma tabela simples: cabecalho em negrito, painel congelado na 1a linha, largura fixa. */
    public Path write(Path file, List<String> header, List<List<String>> rows) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Cliente360");

            CellStyle headerStyle = wb.createCellStyle();
            Font bold = wb.createFont();
            bold.setBold(true);
            headerStyle.setFont(bold);

            Row hr = sheet.createRow(0);
            for (int i = 0; i < header.size(); i++) {
                Cell c = hr.createCell(i);
                c.setCellValue(header.get(i));
                c.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 22 * 256);   // ~22 caracteres (sem AWT/autoSize)
            }

            int r = 1;
            for (List<String> line : rows) {
                Row row = sheet.createRow(r++);
                for (int i = 0; i < line.size(); i++) {
                    String v = line.get(i);
                    row.createCell(i).setCellValue(v == null ? "" : v);
                }
            }
            sheet.createFreezePane(0, 1);

            if (file.getParent() != null) Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file)) {
                wb.write(out);
            }
            return file.toAbsolutePath();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao gerar o .xlsx " + file + ": " + e.getMessage(), e);
        }
    }
}
