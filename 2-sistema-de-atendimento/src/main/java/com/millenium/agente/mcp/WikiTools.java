package com.millenium.agente.mcp;

import com.millenium.agente.core.port.WikiPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Tool de conhecimento do MCP. A wiki e fonte confiavel e separada dos dados do cliente
 * (tom, tratativas, principios, perguntas de descoberta).
 */
@Component
public class WikiTools {

    private static final Logger log = LoggerFactory.getLogger(WikiTools.class);

    private final WikiPort wiki;

    public WikiTools(WikiPort wiki) {
        this.wiki = wiki;
    }

    @Tool(name = "consultar_wiki",
            description = "Consulta a wiki de atendimento (tom, tratativas, principios, perguntas de "
                    + "descoberta). Passe um termo para buscar trechos, ou o nome de um documento para "
                    + "ler o conteudo completo. Sem argumento, lista os documentos disponiveis.")
    public String consultWiki(
            @ToolParam(required = false, description = "termo de busca ou nome de um documento da wiki") String consulta) {
        try {
        if (consulta == null || consulta.isBlank()) {
            return "Documentos da wiki: " + String.join(", ", wiki.listDocuments());
        }
        // Se casar exatamente com um documento, devolve o conteudo completo.
        return wiki.findDocument(consulta)
                .orElseGet(() -> wiki.search(consulta));
    
        } catch (Throwable __t) {
            return fail("consultWiki", __t);
        }
    }

    /** Loga o stack completo e devolve ao agente uma mensagem curta (sem vazar o stack). */
    private String fail(String tool, Throwable t) {
        log.error("Falha na tool {}", tool, t);
        return "Erro interno na tool " + tool + ": " + t.getClass().getSimpleName()
                + (t.getMessage() != null ? " - " + t.getMessage() : "")
                + ". O stack completo esta no log do servidor (MilleniumAgenteIA/logs/millenium-mcp.log)."
                + " Nada foi enviado ao cliente.";
    }
}
