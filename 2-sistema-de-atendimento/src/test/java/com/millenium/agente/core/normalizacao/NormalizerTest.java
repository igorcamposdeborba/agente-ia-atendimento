package com.millenium.agente.core.normalizacao;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Teste de componente da normalizacao de chaves - a etapa que mais decide a qualidade do
 * cruzamento (muitas combinacoes de acento/pontuacao/sufixo/prefixo).
 */
class NormalizerTest {

    @Test
    void cnpjKeepsDigitsOnly() {
        assertEquals("12345678000190", Normalizer.cnpj("12.345.678/0001-90"));
        assertNull(Normalizer.cnpj("  "));
        assertNull(Normalizer.cnpj(null));
    }

    @Test
    void phonePrefixesBrazilDdi() {
        assertEquals("5551999110001", Normalizer.phone("(51) 99911-0001"));
        assertEquals("5551999110001", Normalizer.phone("051 99911-0001"));
        // ja com DDI, mantem
        assertEquals("5551999110001", Normalizer.phone("55 51 99911-0001"));
    }

    @Test
    void legalNameRemovesAccentPunctuationAndSuffix() {
        assertEquals("GAMA CONTABILIDADE", Normalizer.legalName("Gama Contabilidade LTDA"));
        assertEquals("ACME SEGURANCA", Normalizer.legalName("ACME Segurança S.A."));
    }

    @Test
    void stripCodeAndClientCodeReadThePrefix() {
        assertEquals("PADARIA SILVA", Normalizer.stripCode("11111- PADARIA SILVA"));
        assertEquals("PADARIA SILVA", Normalizer.stripCode("11111 - PADARIA SILVA"));
        assertEquals("11111", Normalizer.clientCode("11111- PADARIA SILVA"));
        assertEquals("11111", Normalizer.clientCode("11111 - PADARIA SILVA"));
        assertNull(Normalizer.clientCode("SEM PREFIXO"));
    }

    @Test
    void emailDomainsExtractsAfterAtBeforeDot() {
        assertEquals(java.util.List.of("teste"), Normalizer.emailDomains("teste1@teste.com.br"));
        // lista separada por ; -> dominios distintos, em ordem
        assertEquals(java.util.List.of("milleniumtec", "gmail"),
                Normalizer.emailDomains("a@milleniumtec.com; b@gmail.com; c@milleniumtec.com"));
        assertTrue(Normalizer.emailDomains("").isEmpty());
    }

    @Test
    void distinctiveTokensDropGenericTerms() {
        // "CONTABILIDADE" e generico -> nao entra nos distintivos (evita casar so por ele)
        assertTrue(Normalizer.distinctiveTokens("Contabilidade Paim").contains("PAIM"));
        assertTrue(Normalizer.distinctiveTokens("Contabilidade Paim").stream().noneMatch(t -> t.equals("CONTABILIDADE")));
        // tokens (com genericos) mantem tudo
        assertTrue(Normalizer.tokens("Contabilidade Paim").contains("CONTABILIDADE"));
    }
}
