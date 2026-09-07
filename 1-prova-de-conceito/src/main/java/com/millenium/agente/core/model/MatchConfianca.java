package com.millenium.agente.core.model;

/**
 * Marca de confianca do casamento de registros entre fontes (cascata CNPJ -> telefone -> razao social).
 * Apenas EXATO e PROVAVEL entram na fila automatica; INCERTO vai para conferencia humana.
 */
public enum MatchConfianca {
    EXATO,      // casou por CNPJ (so digitos)
    PROVAVEL,   // casou por telefone normalizado via de-para
    INCERTO;    // casou por razao social normalizada (fuzzy) -> revisao humana

    public boolean entraNaFilaAutomatica() {
        return this == EXATO || this == PROVAVEL;
    }
}
