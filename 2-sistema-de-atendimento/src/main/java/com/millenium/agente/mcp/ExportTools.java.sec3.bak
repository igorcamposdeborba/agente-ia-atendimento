package com.millenium.agente.mcp;

import com.millenium.agente.adapter.excel.ExcelSpreadsheet;
import com.millenium.agente.config.MilleniumProperties;
import com.millenium.agente.core.dto.Client360;
import com.millenium.agente.core.dto.InactiveClient;
import com.millenium.agente.core.service.ClientService;
import com.millenium.agente.core.util.ScoreBands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Tool que exporta o <b>cadastro normalizado (Cliente 360)</b> para um Excel (.xlsx) em
 * {@code millenium.output-dir}. Use APENAS quando o atendente pedir explicitamente (gatilho
 * "gerar excel"), tipicamente DEPOIS de rodar o Preventivo/Pos-NPS — ver a skill exportar-cadastro.
 * <p>
 * O arquivo e um documento interno: o <b>CNPJ sai completo</b> (o mascaramento vale so no canal
 * MCP&lt;-&gt;IA). Como quem grava e o MCP, o CNPJ completo nunca e enviado ao modelo.
 */
@Component
public class ExportTools {

    private static final Logger log = LoggerFactory.getLogger(ExportTools.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH-mm");
    private static final Pattern INDEX = Pattern.compile("^(\\d+)\\s*-");

    private static final List<String> HEADER = List.of(
            "Razão social", "CNPJ", "Representante", "Telefone", "Produtos", "Valor (soma NF)",
            "Status", "Antiguidade (meses)", "Inatividade (meses)", "Última interação", "Última visita",
            "Preventiva vencida", "RFM recência", "RFM antiguidade", "RFM valor", "RFM score",
            "Gatilhos", "NPS nota", "NPS faixa", "NPS data", "NPS comentário", "Confiança",
            "Conferir?", "Fontes casadas");

    private final MilleniumProperties props;
    private final ClientService service;
    private final ExcelSpreadsheet excel = new ExcelSpreadsheet();

    public ExportTools(MilleniumProperties props, ClientService service) {
        this.props = props;
        this.service = service;
    }

    @Tool(name = "exportar_cadastro_xlsx",
            description = "Exporta o cadastro normalizado (Cliente 360) para um Excel (.xlsx) na pasta "
                    + "de saida e retorna o caminho. Use SOMENTE quando o atendente pedir (gatilho "
                    + "\"gerar excel\"), em geral apos rodar o Preventivo ou o Pos-NPS. Uma linha por "
                    + "cliente, colunas de cadastro/produtos/RFM/gatilhos/NPS. O CNPJ sai completo no "
                    + "arquivo (documento interno). Nome ordenavel com indice + data/hora.")
    public String exportRegistryXlsx(
            @ToolParam(required = false, description = "rótulo base do arquivo (padrão: \"cadastro cliente 360\")") String nomeArquivo) {

      try {
        List<Client360> clients = service.consolidatedClients();
        List<List<String>> rows = new ArrayList<>(clients.size());
        for (Client360 c : clients) {
            InactiveClient ci = service.evaluate(c);
            rows.add(Arrays.asList(
                    c.legalName(),
                    ClientService.formatCnpj(c.cnpj()),                 // CNPJ COMPLETO (arquivo interno)
                    c.representative(),
                    c.phone(),
                    String.join(", ", c.products()),
                    Formatter.money(c.totalValue()),
                    c.status(),
                    String.valueOf(ci.tenureMonths()),
                    ci.inactivityMonths() == null ? "" : String.valueOf(ci.inactivityMonths()),
                    ci.lastInteraction() == null ? "" : ci.lastInteraction().toString(),
                    c.lastVisit() == null ? "" : c.lastVisit().toString(),
                    ci.maintenanceOverdue() ? "SIM" : "NAO",
                    String.valueOf(ci.rfm().recency()),
                    String.valueOf(ci.rfm().tenure()),
                    String.valueOf(ci.rfm().monetization()),
                    String.valueOf(ci.rfm().score()),
                    String.join("; ", ci.triggers()),
                    c.npsScore() == null ? "" : String.valueOf(c.npsScore()),
                    ScoreBands.npsBand(c.npsScore()),
                    c.npsDate() == null ? "" : c.npsDate().toString(),
                    c.npsComment(),
                    c.confidence() == null ? "" : c.confidence().name(),
                    ci.needsHumanReview() ? "SIM" : "NAO",
                    String.join("; ", c.matchedSources())));
        }

        Path dir = Path.of(props.getOutputDir());
        String base = (nomeArquivo != null && !nomeArquivo.isBlank()) ? nomeArquivo : "cadastro cliente 360";
        LocalDateTime now = LocalDateTime.now();
        String name = nextIndex(dir) + "- " + baseLabel(base) + " - "
                + now.format(DATE) + " as " + now.format(TIME) + ".xlsx";

        Path saved = excel.write(dir.resolve(name), HEADER, rows);
        return "Excel do cadastro (Cliente 360) salvo em: " + saved
                + " — " + rows.size() + " cliente(s). Documento interno (CNPJ completo); revise antes de compartilhar.";
      } catch (Throwable t) {
        log.error("Falha ao exportar cadastro .xlsx", t);
        return "Não consegui gerar o Excel do cadastro: " + t.getClass().getSimpleName()
                + (t.getMessage() != null ? " - " + t.getMessage() : "")
                + ". Verifique a pasta de saída (millenium.output-dir) e se o jar foi reempacotado com 'mvn clean package'.";
      }
    }

    /** Proximo indice para ordenar os arquivos: maior "N-" existente na pasta + 1 (comeca em 1). */
    private int nextIndex(Path dir) {
        int max = 0;
        if (Files.isDirectory(dir)) {
            try (Stream<Path> s = Files.list(dir)) {
                for (Path p : (Iterable<Path>) s::iterator) {
                    Matcher m = INDEX.matcher(p.getFileName().toString());
                    if (m.find()) {
                        try {
                            max = Math.max(max, Integer.parseInt(m.group(1)));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            } catch (IOException ignored) {
            }
        }
        return max + 1;
    }

    /** Rotulo do arquivo: remove so caracteres invalidos de nome (mantem acentos e espacos). */
    private String baseLabel(String s) {
        String clean = s.replaceAll("[\\\\/:*?\"<>|]", " ").replaceAll("\\s+", " ").strip();
        if (clean.isEmpty()) return "cadastro cliente 360";
        return clean.length() > 60 ? clean.substring(0, 60).strip() : clean;
    }
}
