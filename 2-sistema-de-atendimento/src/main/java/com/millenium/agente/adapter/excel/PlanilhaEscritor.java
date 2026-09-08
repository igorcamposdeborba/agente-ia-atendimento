package com.millenium.agente.adapter.excel;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Escreve uma tabela simples em .xlsx (Apache POI XSSF) — sem Python. Cabecalho em negrito,
 * painel congelado na 1a linha e larguras fixas (evita {@code autoSizeColumn}, que usa AWT e pode
 * falhar em ambiente headless). Usado pela tool {@code exportar_cadastro_xlsx} para gravar o
 * Cliente 360 normalizado em {@code millenium.output-dir}.
 */
public class PlanilhaEscritor {

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
