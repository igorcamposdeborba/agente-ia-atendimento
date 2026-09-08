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
import java.util.List;

/**
 * Le a primeira aba de um .xlsx. Alem do modo "mapa cabecalho -> valor" (usado pelos adaptadores
 * antigos), expoe {@link #matriz(Path)}, que devolve a planilha como uma <b>matriz de strings</b>
 * (linha 0 = cabecalhos), com celulas de <b>data normalizadas para ISO</b> (yyyy-MM-dd) e numeros
 * inteiros sem o sufixo ".0". A leitura posicional e necessaria para o NPS (colunas D/E do field
 * mapping, cujos cabecalhos sao perguntas longas).
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

    /**
     * Planilha como matriz de strings (linha 0 = cabecalhos). Celulas de data -> ISO (yyyy-MM-dd);
     * numeros inteiros -> sem ".0". Linhas totalmente vazias sao descartadas.
     */
    public List<String[]> matriz(Path arquivo) {
        List<String[]> linhas = new ArrayList<>();
        if (arquivo == null || !Files.exists(arquivo)) {
            log.warn("Planilha nao encontrada: {} (retornando vazio)", arquivo);
            return linhas;
        }
        try (InputStream in = Files.newInputStream(arquivo);
             Workbook wb = new XSSFWorkbook(in)) {
            Sheet sheet = wb.getSheetAt(0);
            int firstRow = sheet.getFirstRowNum();
            int lastCol = 0;
            Row header = sheet.getRow(firstRow);
            if (header != null) lastCol = header.getLastCellNum();
            for (int i = firstRow; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                int nCols = Math.max(lastCol, row.getLastCellNum());
                String[] vals = new String[Math.max(nCols, 0)];
                boolean vazia = true;
                for (int c = 0; c < vals.length; c++) {
                    String v = valorCelula(row.getCell(c));
                    if (!v.isEmpty()) vazia = false;
                    vals[c] = v;
                }
                if (!vazia) linhas.add(vals);
            }
            return linhas;
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao ler a planilha " + arquivo + ": " + e.getMessage(), e);
        }
    }

    private String valorCelula(Cell cell) {
        if (cell == null) return "";
        if (cell.getCellType() == CellType.NUMERIC
                || (cell.getCellType() == CellType.FORMULA && cell.getCachedFormulaResultType() == CellType.NUMERIC)) {
            if (DateUtil.isCellDateFormatted(cell)) {
                try {
                    return cell.getLocalDateTimeCellValue().toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
                } catch (Exception ignore) {
                    // cai para o formatador padrao
                }
            }
            double d = cell.getNumericCellValue();
            if (d == Math.floor(d) && !Double.isInfinite(d)) {
                return String.valueOf((long) d);   // inteiro sem ".0" (codigos, telefones, notas)
            }
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
        } catch (Exception ignore) { /* tenta outros */ }
        for (String pat : new String[]{"dd/MM/yyyy", "d/M/yyyy", "dd/MM/yy"}) {
            try {
                return LocalDate.parse(s.length() > 10 ? s.substring(0, 10) : s, DateTimeFormatter.ofPattern(pat));
            } catch (Exception ignore) { /* proximo */ }
        }
        log.warn("Data invalida: '{}'", v);
        return null;
    }
}
