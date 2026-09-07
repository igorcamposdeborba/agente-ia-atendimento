package com.millenium.agente.mcp;

import com.millenium.agente.core.port.WikiPort;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Tool de conhecimento do MCP. A wiki e fonte confiavel e separada dos dados do cliente
 * (tom, tratativas, principios, perguntas de descoberta).
 */
@Component
public class WikiTools {

    private final WikiPort wiki;

    public WikiTools(WikiPort wiki) {
        this.wiki = wiki;
    }

    @Tool(name = "consultar_wiki",
            description = "Consulta a wiki de atendimento (tom, tratativas, principios, perguntas de "
                    + "descoberta). Passe um termo para buscar trechos, ou o nome de um documento para "
                    + "ler o conteudo completo. Sem argumento, lista os documentos disponiveis.")
    public String consultarWiki(
            @ToolParam(required = false, description = "termo de busca ou nome de um documento da wiki") String consulta) {
        if (consulta == null || consulta.isBlank()) {
            return "Documentos da wiki: " + String.join(", ", wiki.listarDocumentos());
        }
        // Se casar exatamente com um documento, devolve o conteudo completo.
        return wiki.buscarDocumento(consulta)
                .orElseGet(() -> wiki.buscar(consulta));
    }
}
