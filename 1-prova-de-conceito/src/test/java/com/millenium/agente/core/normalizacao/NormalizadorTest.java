package com.millenium.agente.core.normalizacao;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NormalizadorTest {

    @Test
    void cnpjMantemApenasDigitos() {
        assertEquals("12345678000190", Normalizador.cnpj("12.345.678/0001-90"));
        assertNull(Normalizador.cnpj("  "));
    }

    @Test
    void telefonePrefixaDdiBrasil() {
        assertEquals("5551999110001", Normalizador.telefone("(51) 99911-0001"));
        assertEquals("5551999110001", Normalizador.telefone("051 99911-0001"));
        // ja com DDI, mantem
        assertEquals("5551999110001", Normalizador.telefone("55 51 99911-0001"));
    }

    @Test
    void razaoSocialRemoveAcentoPontuacaoESufixo() {
        assertEquals("GAMA CONTABILIDADE", Normalizador.razaoSocial("Gama Contabilidade LTDA"));
        assertEquals("ACME SEGURANCA", Normalizador.razaoSocial("ACME Segurança S.A."));
    }
}
