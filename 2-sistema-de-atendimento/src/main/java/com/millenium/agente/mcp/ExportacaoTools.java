package com.millenium.agente.mcp;

import com.millenium.agente.adapter.excel.PlanilhaExcel;
import com.millenium.agente.config.MilleniumProperties;
import com.millenium.agente.core.dto.Cliente360;
import com.millenium.agente.core.dto.ClienteInativo;
import com.millenium.agente.core.service.ClienteService;
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
public class ExportacaoTools {

    private static final Logger log = LoggerFactory.getLogger(ExportacaoTools.class);
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH-mm");
    private static final Pattern INDICE = Pattern.compile("^(\\d+)\\s*-");

    private static final List<String> CABECALHO = List.of(
            "Razão social", "CNPJ", "Representante", "Telefone", "Produtos", "Valor (soma NF)",
            "Status", "Antiguidade (meses)", "Inatividade (meses)", "Última interação", "Última visita",
            "Preventiva vencida", "RFM recência", "RFM antiguidade", "RFM valor", "RFM score",
            "Gatilhos", "NPS nota", "NPS faixa", "NPS data", "NPS comentário", "Confiança",
            "Conferir?", "Fontes casadas");

    private final MilleniumProperties props;
    private final ClienteService service;
    private final PlanilhaExcel excel = new PlanilhaExcel();

    public ExportacaoTools(MilleniumProperties props, ClienteService service) {
        this.props = props;
        this.service = service;
    }

    @Tool(name = "exportar_cadastro_xlsx",
            description = "Exporta o cadastro normalizado (Cliente 360) para um Excel (.xlsx) na pasta "
                    + "de saida e retorna o caminho. Use SOMENTE quando o atendente pedir (gatilho "
                    + "\"gerar excel\"), em geral apos rodar o Preventivo ou o Pos-NPS. Uma linha por "
                    + "cliente, colunas de cadastro/produtos/RFM/gatilhos/NPS. O CNPJ sai completo no "
                    + "arquivo (documento interno). Nome ordenavel com indice + data/hora.")
    public String exportarCadastroXlsx(
            @ToolParam(required = false, description = "rótulo base do arquivo (padrão: \"cadastro cliente 360\")") String nomeArquivo) {

      try {
        List<Cliente360> clientes = service.clientesConsolidados();
        List<List<String>> linhas = new ArrayList<>(clientes.size());
        for (Cliente360 c : clientes) {
            ClienteInativo ci = service.avaliar(c);
            linhas.add(Arrays.asList(
                    c.razaoSocial(),
                    ClienteService.formatarCnpj(c.cnpj()),                 // CNPJ COMPLETO (arquivo interno)
                    c.representante(),
                    c.telefone(),
                    String.join(", ", c.produtos()),
                    Formatador.moeda(c.valorMensalTotal()),
                    c.status(),
                    String.valueOf(ci.antiguidadeMeses()),
                    ci.inatividadeMeses() == null ? "" : String.valueOf(ci.inatividadeMeses()),
                    ci.ultimaInteracao() == null ? "" : ci.ultimaInteracao().toString(),
                    c.ultimaVisita() == null ? "" : c.ultimaVisita().toString(),
                    ci.preventivaVencida() ? "SIM" : "NAO",
                    String.valueOf(ci.rfm().recencia()),
                    String.valueOf(ci.rfm().antiguidade()),
                    String.valueOf(ci.rfm().monetizacao()),
                    String.valueOf(ci.rfm().score()),
                    String.join("; ", ci.gatilhos()),
                    c.npsNota() == null ? "" : String.valueOf(c.npsNota()),
                    faixaNps(c.npsNota()),
                    c.npsData() == null ? "" : c.npsData().toString(),
                    c.npsComentario(),
                    c.confianca() == null ? "" : c.confianca().name(),
                    ci.precisaConferenciaHumana() ? "SIM" : "NAO",
                    String.join("; ", c.fontesCasadas())));
        }

        Path dir = Path.of(props.getOutputDir());
        String base = (nomeArquivo != null && !nomeArquivo.isBlank()) ? nomeArquivo : "cadastro cliente 360";
        LocalDateTime agora = LocalDateTime.now();
        String nome = proximoIndice(dir) + "- " + rotuloBase(base) + " - "
                + agora.format(DATA) + " as " + agora.format(HORA) + ".xlsx";

        Path salvo = excel.escrever(dir.resolve(nome), CABECALHO, linhas);
        return "Excel do cadastro (Cliente 360) salvo em: " + salvo
                + " — " + linhas.size() + " cliente(s). Documento interno (CNPJ completo); revise antes de compartilhar.";
      } catch (Throwable t) {
        log.error("Falha ao exportar cadastro .xlsx", t);
        return "Não consegui gerar o Excel do cadastro: " + t.getClass().getSimpleName()
                + (t.getMessage() != null ? " - " + t.getMessage() : "")
                + ". Verifique a pasta de saída (millenium.output-dir) e se o jar foi reempacotado com 'mvn clean package'.";
      }
    }

    private static String faixaNps(Integer nota) {
        if (nota == null) return "";
        if (nota >= 9) return "promotor";
        if (nota >= 7) return "passivo";
        return "detrator";
    }

    /** Proximo indice para ordenar os arquivos: maior "N-" existente na pasta + 1 (comeca em 1). */
    private int proximoIndice(Path dir) {
        int max = 0;
        if (Files.isDirectory(dir)) {
            try (Stream<Path> s = Files.list(dir)) {
                for (Path p : (Iterable<Path>) s::iterator) {
                    Matcher m = INDICE.matcher(p.getFileName().toString());
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
    private String rotuloBase(String s) {
        String limpo = s.replaceAll("[\\\\/:*?\"<>|]", " ").replaceAll("\\s+", " ").strip();
        if (limpo.isEmpty()) return "cadastro cliente 360";
        return limpo.length() > 60 ? limpo.substring(0, 60).strip() : limpo;
    }
}
