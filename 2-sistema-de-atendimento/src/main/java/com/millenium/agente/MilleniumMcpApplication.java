package com.millenium.agente;

import com.millenium.agente.config.MilleniumProperties;
import com.millenium.agente.mcp.ClientTools;
import com.millenium.agente.mcp.DocumentTools;
import com.millenium.agente.mcp.ExportTools;
import com.millenium.agente.mcp.WikiTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Servidor MCP da Millenium (Fase 1 - PoC Excel). Rodado localmente pelo Claude Desktop via stdio.
 * Expoe as tools ficha_cliente, preventivo_contatos, clientes_inativos, historico_relacionamento,
 * recarregar_dados, consultar_wiki, salvar_documento_docx e exportar_cadastro_xlsx.
 */
@SpringBootApplication
@EnableConfigurationProperties(MilleniumProperties.class)
public class MilleniumMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(MilleniumMcpApplication.class, args);
    }

    /** Registra os metodos anotados com @Tool como tools do MCP server. */
    @Bean
    public ToolCallbackProvider milleniumTools(ClientTools clientTools, WikiTools wikiTools,
                                               DocumentTools documentTools, ExportTools exportTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(clientTools, wikiTools, documentTools, exportTools)
                .build();
    }
}
