package com.millenium.agente.mcp;

import com.millenium.agente.adapter.docx.DocxWriter;
import com.millenium.agente.config.MilleniumProperties;
import com.millenium.agente.core.service.ClientService;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Tool que salva documentos Word (.docx) no computador, gerados pelo próprio MCP (Apache POI).
 * Não depende de Python nem da capacidade de "code execution" do plano.
 * <p>
 * No documento gerado o <b>CNPJ aparece completo</b> (é documento interno do atendente); o
 * mascaramento vale apenas no canal MCP&lt;-&gt;IA. O MCP faz a troca mascarado-&gt;completo ao gravar,
 * então o CNPJ completo nunca é enviado ao modelo.
 */
@Component
public class DocumentTools {

    private static final Logger log = LoggerFactory.getLogger(DocumentTools.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH-mm");
    private static final Pattern INDEX = Pattern.compile("^(\\d+)\\s*-");

    private final MilleniumProperties props;
    private final ClientService service;
    private final DocxWriter writer = new DocxWriter();

    public DocumentTools(MilleniumProperties props, ClientService service) {
        this.props = props;
        this.service = service;
    }

    @Tool(name = "salvar_documento_docx",
            description = "Salva um documento Word (.docx) no computador e retorna o caminho. O CNPJ "
                    + "no arquivo sai COMPLETO (documento interno) - passe o CNPJ como veio das tools "
                    + "(mascarado) que o MCP completa sozinho. Conteúdo em marcação leve: '# ' título, "
                    + "'## '/'### ' subtítulos, '- ' item de lista, linha em branco separa parágrafos, "
                    + "'Rótulo: valor' deixa o rótulo em negrito. Use para o plano do Preventivo ou a "
                    + "resposta do Pós-NPS. O nome do arquivo é gerado com índice e data/hora (ordenável). "
                    + "Sempre marque como rascunho para revisão humana.")
    public String saveDocumentDocx(
            @ToolParam(description = "título do documento (vira o título grande no topo)") String titulo,
            @ToolParam(description = "corpo do documento com marcação leve (ver descrição)") String conteudo,
            @ToolParam(required = false, description = "rótulo base do arquivo (ex.: \"plano de atendimento\"); o índice e a data/hora são adicionados") String nomeArquivo) {

        // Log de ENTRADA: se este aparecer no arquivo de log mas nao houver "Documento Word salvo"
        // nem stack de erro logo abaixo, o travamento esta no write do POI. Se NAO aparecer,
        // a chamada nem chegou ao servidor (jar velho/travado, tool nao registrada).
        log.info("Gerando .docx: titulo=\"{}\", tamanho do conteudo={} chars, nomeArquivo={}",
                titulo, conteudo == null ? 0 : conteudo.length(), nomeArquivo);
        // Captura Throwable (nao so Exception): um Error como NoClassDefFoundError do POI-XWPF num
        // jar mal reempacotado mataria a thread e deixaria o cliente esperando (o "travou 4 min").
        try {
            Path dir = Path.of(props.getOutputDir());
            String base = baseLabel(nomeArquivo != null && !nomeArquivo.isBlank() ? nomeArquivo : titulo);
            LocalDateTime now = LocalDateTime.now();
            String name = nextIndex(dir) + "- " + base + " - "
                    + now.format(DATE) + " as " + now.format(TIME) + ".docx";

            // CNPJ completo no arquivo (mascaramento vale só no canal MCP<->IA)
            String finalTitle = service.revealCnpjs(titulo);
            String finalContent = service.revealCnpjs(conteudo);

            Path saved = writer.write(dir.resolve(name), finalTitle, finalContent);
            log.info("Documento Word salvo em: {}", saved);
            return "Documento Word salvo em: " + saved
                    + "\n(Rascunho para revisão humana — abra no Word, revise e só então use.)";
        } catch (Throwable t) {
            log.error("Falha ao gerar .docx", t);
            return "Não consegui gerar o .docx: " + t.getClass().getSimpleName()
                    + (t.getMessage() != null ? " - " + t.getMessage() : "")
                    + ". Verifique a pasta de saída (millenium.output-dir) e se o jar foi reempacotado "
                    + "com 'mvn clean package' (POI/XWPF completo). Nada foi enviado ao cliente.";
        }
    }

    /** Próximo índice para ordenar os arquivos: máximo "N-" existente na pasta + 1 (começa em 1). */
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

    /** Rótulo do arquivo: remove só caracteres inválidos de nome (mantém acentos e espaços). */
    private String baseLabel(String s) {
        if (s == null) return "documento";
        String clean = s.replaceAll("[\\\\/:*?\"<>|]", " ").replaceAll("\\s+", " ").strip();
        if (clean.isEmpty()) return "documento";
        return clean.length() > 60 ? clean.substring(0, 60).strip() : clean;
    }
}
