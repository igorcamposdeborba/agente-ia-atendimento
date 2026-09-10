package com.millenium.agente.core.port;

import java.util.List;
import java.util.Optional;

/**
 * Porta de saida para a Wiki (conhecimento curado em Markdown). Consumida pela tool consultar_wiki.
 */
public interface WikiPort {

    /** Nomes dos documentos disponiveis na wiki. */
    List<String> listDocuments();

    /** Conteudo de um documento pelo nome (com ou sem extensao). */
    Optional<String> findDocument(String name);

    /** Busca por termo no conteudo da wiki; retorna trechos relevantes com o documento de origem. */
    String search(String query);
}
