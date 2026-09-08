package com.millenium.agente;

import com.millenium.agente.config.MilleniumProperties;
import com.millenium.agente.mcp.ClienteTools;
import com.millenium.agente.mcp.DocumentoTools;
import com.millenium.agente.mcp.ExportacaoTools;
import com.millenium.agente.mcp.WikiTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Servidor MCP da Millenium (Fase 1 - PoC Excel). Rodado localmente pelo Claude Desktop via stdio.
 * Expoe as tools buscar_cliente, clientes_inativos, historico_relacionamento, recarregar_dados e
 * consultar_wiki.
 */
@SpringBootApplication
@EnableConfigurationProperties(MilleniumProperties.class)
public class MilleniumMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(MilleniumMcpApplication.class, args);
    }

    /** Registra os metodos anotados com @Tool como tools do MCP server. */
    @Bean
    public ToolCallbackProvider milleniumTools(ClienteTools clienteTools, WikiTools wikiTools,
                                               DocumentoTools documentoTools, ExportacaoTools exportacaoTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(clienteTools, wikiTools, documentoTools, exportacaoTools)
                .build();
    }
}
