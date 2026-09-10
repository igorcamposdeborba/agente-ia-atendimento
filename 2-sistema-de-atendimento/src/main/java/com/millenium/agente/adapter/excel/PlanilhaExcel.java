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
 * I/O de planilhas {@code .xlsx} via Apache POI, num único lugar. Reúne o que antes eram cinco
 * classes (leitor de baixo nível, leitor genérico por cabeçalho, especialização do NPS, a interface
 * de estratégia e o escritor). A API é pequena e direta:
 * <ul>
 *   <li><b>Leitura crua</b> — {@link #posicional(Path)}: linhas por índice de coluna, com datas já
 *       em ISO ({@code yyyy-MM-dd}) e inteiros sem o sufixo ".0". Usada pelo NPS, cujo cabeçalho é
 *       uma pergunta longa e por isso é lido por posição.</li>
 *   <li><b>Leitura tipada</b> — {@link #porCabecalho(Path, Class)}: cada linha vira um record via
 *       Jackson (o cabeçalho normalizado casa com os {@code @JsonProperty}). Serve às planilhas com
 *       cabeçalho de coluna (Contatos, Sistema, NF, Megazap).</li>
 *   <li><b>Conversões</b> — {@link #parseData}, {@link #parseValor}, {@link #parseInteiro} e
 *       {@link #norm} (estáticas), aplicadas na consolidação em {@link ExcelFonteDados}.</li>
 *   <li><b>Escrita</b> — {@link #escrever(Path, List, List)}: grava uma tabela simples (cabeçalho em
 *       negrito, painel congelado, largura fixa — sem {@code autoSizeColumn}, que usa AWT).</li>
 * </ul>
 */
public final class PlanilhaExcel {

    private static final Logger log = LoggerFactory.getLogger(PlanilhaExcel.class);
    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final DataFormatter formatter = new DataFormatter();

    // ============================ Leitura ============================

    /** Linhas cruas (linha 0 = cabeçalhos). Datas em ISO; inteiros sem ".0". Vazio se o arquivo falta. */
    public List<String[]> posicional(Path arquivo) {
        if (arquivo == null || !Files.exists(arquivo)) {
            log.warn("Planilha não encontrada: {} (retornando vazio)", arquivo);
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

    /**
     * Cada linha de dados desserializada no record {@code T} pelo Jackson: monta o mapa
     * {@code cabeçalho normalizado -> valor} (células vazias ficam de fora, virando campo {@code null})
     * e converte. Sem intermediário exposto — leitura por cabeçalho num passo só.
     */
    public <T> List<T> porCabecalho(Path arquivo, Class<T> tipo) {
        List<String[]> linhas = posicional(arquivo);
        if (linhas.isEmpty()) return List.of();
        String[] header = linhas.get(0);
        List<T> out = new ArrayList<>(linhas.size() - 1);
        for (int i = 1; i < linhas.size(); i++) {
            String[] row = linhas.get(i);
            Map<String, String> m = new LinkedHashMap<>();
            for (int c = 0; c < header.length; c++) {
                String v = c < row.length ? row[c] : "";
                if (!v.isEmpty()) m.put(norm(header[c]), v);
            }
            out.add(JSON.convertValue(m, tipo));
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
                } catch (Exception ignore) { /* cai para o formatador padrão */ }
            }
            double d = cell.getNumericCellValue();
            if (d == Math.floor(d) && !Double.isInfinite(d)) return String.valueOf((long) d);
            return BigDecimal.valueOf(d).stripTrailingZeros().toPlainString();
        }
        return formatter.formatCellValue(cell).trim();
    }

    // ============================ Conversões ============================

    /** Normaliza um cabeçalho/coluna: sem acento, minúsculo, só [a-z0-9] separados por espaço. */
    public static String norm(String s) {
        if (s == null) return "";
        String semAcento = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcento.toLowerCase().replaceAll("[^a-z0-9]+", " ").trim();
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
            } catch (Exception ignore) { /* próximo */ }
        }
        log.warn("Data inválida: '{}'", v);
        return null;
    }

    /** Valor monetário BR/US -> BigDecimal (ou null). */
    public static BigDecimal parseValor(String v) {
        if (v == null || v.isBlank()) return null;
        String s = v.replaceAll("[^0-9,.-]", "");
        int vlgVirg = s.lastIndexOf(','), vlgPonto = s.lastIndexOf('.');
        if (vlgVirg >= 0 && vlgPonto >= 0) s = (vlgVirg > vlgPonto) ? s.replace(".", "").replace(',', '.') : s.replace(",", "");
        else if (vlgVirg >= 0) s = s.replace(',', '.');
        try { return new BigDecimal(s); } catch (Exception e) { return null; }
    }

    /** Inteiro tolerante (só dígitos) -> Integer (ou null). */
    public static Integer parseInteiro(String v) {
        if (v == null || v.isBlank()) return null;
        try { return Integer.valueOf(v.replaceAll("\\D", "")); } catch (Exception e) { return null; }
    }

    // ============================ Escrita ============================

    /** Grava uma tabela simples: cabeçalho em negrito, painel congelado na 1ª linha, largura fixa. */
    public Path escrever(Path arquivo, List<String> cabecalho, List<List<String>> linhas) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Cliente360");

            CellStyle estiloCabecalho = wb.createCellStyle();
            Font negrito = wb.createFont();
            negrito.setBold(true);
            estiloCabecalho.setFont(negrito);

            Row hr = sheet.createRow(0);
            for (int i = 0; i < cabecalho.size(); i++) {
                Cell c = hr.createCell(i);
                c.setCellValue(cabecalho.get(i));
                c.setCellStyle(estiloCabecalho);
                sheet.setColumnWidth(i, 22 * 256);   // ~22 caracteres (sem AWT/autoSize)
            }

            int r = 1;
            for (List<String> linha : linhas) {
                Row row = sheet.createRow(r++);
                for (int i = 0; i < linha.size(); i++) {
                    String v = linha.get(i);
                    row.createCell(i).setCellValue(v == null ? "" : v);
                }
            }
            sheet.createFreezePane(0, 1);

            if (arquivo.getParent() != null) Files.createDirectories(arquivo.getParent());
            try (OutputStream out = Files.newOutputStream(arquivo)) {
                wb.write(out);
            }
            return arquivo.toAbsolutePath();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao gerar o .xlsx " + arquivo + ": " + e.getMessage(), e);
        }
    }
}
