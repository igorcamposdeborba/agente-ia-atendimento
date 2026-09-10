package com.millenium.agente.core.dto;

/**
 * Marca de confianca do casamento de registros entre fontes (cascata CNPJ -> telefone -> razao social).
 * Apenas EXATO e PROVAVEL entram na fila automatica; INCERTO vai para conferencia humana
 * (ver ClientService, que trata o INCERTO como "needsHumanReview").
 * <p>
 * Os NOMES das constantes (EXATO/PROVAVEL/INCERTO) sao exibidos ao agente/atendente (via
 * {@code .name()} na ficha e no Excel), entao permanecem em portugues como saida do produto.
 */
public enum MatchConfidence {
    EXATO,      // casou por CNPJ (so digitos)
    PROVAVEL,   // casou por telefone normalizado via de-para
    INCERTO     // casou por razao social/nome fantasia normalizados (fuzzy) -> revisao humana
}
