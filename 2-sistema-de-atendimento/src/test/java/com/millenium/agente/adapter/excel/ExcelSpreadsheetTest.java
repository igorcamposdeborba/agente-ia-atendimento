package com.millenium.agente.adapter.excel;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Teste de componente das conversoes do leitor de Excel (I/O com muitas combinacoes de formato):
 * datas em varios formatos, valores BR/US e inteiros tolerantes, alem da normalizacao de cabecalho.
 */
class ExcelSpreadsheetTest {

    @Test
    void parsesDatesInSeveralFormats() {
        assertEquals(LocalDate.of(2026, 8, 28), ExcelSpreadsheet.parseDate("2026-08-28"));
        assertEquals(LocalDate.of(2026, 8, 28), ExcelSpreadsheet.parseDate("2026-08-28 14:18:15"));
        assertEquals(LocalDate.of(2026, 8, 28), ExcelSpreadsheet.parseDate("28/08/2026"));
        assertNull(ExcelSpreadsheet.parseDate(""));
        assertNull(ExcelSpreadsheet.parseDate("data invalida"));
    }

    @Test
    void parsesAmountsInBrAndUsFormats() {
        assertEquals(new BigDecimal("1234.56"), ExcelSpreadsheet.parseAmount("1.234,56"));   // BR
        assertEquals(new BigDecimal("1234.56"), ExcelSpreadsheet.parseAmount("1,234.56"));   // US
        assertEquals(new BigDecimal("1234.56"), ExcelSpreadsheet.parseAmount("1234.56"));
        assertEquals(new BigDecimal("2000.00"), ExcelSpreadsheet.parseAmount("R$ 2.000,00"));
        assertNull(ExcelSpreadsheet.parseAmount(""));
    }

    @Test
    void parsesIntegersTolerantly() {
        assertEquals(10, ExcelSpreadsheet.parseInteger("10"));
        assertEquals(8, ExcelSpreadsheet.parseInteger(" 8 "));
        assertNull(ExcelSpreadsheet.parseInteger(""));
        assertNull(ExcelSpreadsheet.parseInteger("abc"));
    }

    @Test
    void normalizesHeaders() {
        assertEquals("inicio vigencia", ExcelSpreadsheet.norm("Início Vigência"));
        assertEquals("valor total nf", ExcelSpreadsheet.norm("Valor Total NF"));
        assertEquals("", ExcelSpreadsheet.norm(null));
    }
}
