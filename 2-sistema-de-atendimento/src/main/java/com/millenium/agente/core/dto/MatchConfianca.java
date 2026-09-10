package com.millenium.agente.core.dto;

/**
 * Marca de confianca do casamento de registros entre fontes (cascata CNPJ -> telefone -> razao social).
 * Apenas EXATO e PROVAVEL entram na fila automatica; INCERTO vai para conferencia humana
 * (ver ClienteService, que trata o INCERTO como "precisaConferenciaHumana").
 */
public enum MatchConfianca {
    EXATO,      // casou por CNPJ (so digitos)
    PROVAVEL,   // casou por telefone normalizado via de-para
    INCERTO     // casou por razao social/nome fantasia normalizados (fuzzy) -> revisao humana
}
