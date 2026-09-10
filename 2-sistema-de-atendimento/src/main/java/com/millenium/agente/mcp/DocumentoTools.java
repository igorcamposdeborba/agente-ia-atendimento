package com.millenium.agente.mcp;

import com.millenium.agente.adapter.docx.DocxWriter;
import com.millenium.agente.config.MilleniumProperties;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Tool que salva documentos Word (.docx) no computador, gerados pelo próprio MCP (Apache POI).
 * <p>
 * No documento gerado o <b>CNPJ aparece completo</b> (é documento interno do atendente); o
 * mascaramento vale apenas no canal MCP-IA.
 */
@Component
public class DocumentoTools {

    private static final Logger log = LoggerFactory.getLogger(DocumentoTools.class);
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH-mm");
    private static final Pattern INDICE = Pattern.compile("^(\\d+)\\s*-");

    private final MilleniumProperties props;
    private final ClienteService service;
    private final DocxWriter writer = new DocxWriter();

    public DocumentoTools(MilleniumProperties props, ClienteService service) {
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
    public String salvarDocumentoDocx(
            @ToolParam(description = "título do documento (vira o título grande no topo)") String titulo,
            @ToolParam(description = "corpo do documento com marcação leve (ver descrição)") String conteudo,
            @ToolParam(required = false, description = "rótulo base do arquivo (ex.: \"plano de atendimento\"); o índice e a data/hora são adicionados") String nomeArquivo) {

        log.info("Gerando .docx: titulo=\"{}\", tamanho do conteudo={} chars, nomeArquivo={}",
                titulo, conteudo == null ? 0 : conteudo.length(), nomeArquivo);
        try {
            Path dir = Path.of(props.getOutputDir());
            String base = rotuloBase(nomeArquivo != null && !nomeArquivo.isBlank() ? nomeArquivo : titulo);
            LocalDateTime agora = LocalDateTime.now();
            String nome = proximoIndice(dir) + "- " + base + " - "
                    + agora.format(DATA) + " as " + agora.format(HORA) + ".docx";

            // CNPJ completo no arquivo (mascaramento vale só no canal MCP<->IA)
            String tituloFinal = service.revelarCnpjs(titulo);
            String conteudoFinal = service.revelarCnpjs(conteudo);

            Path salvo = writer.escrever(dir.resolve(nome), tituloFinal, conteudoFinal);
            log.info("Documento Word salvo em: {}", salvo);
            return "Documento Word salvo em: " + salvo
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

    /** Rótulo do arquivo: remove só caracteres inválidos de nome (mantém acentos e espaços). */
    private String rotuloBase(String s) {
        if (s == null) return "documento";
        String limpo = s.replaceAll("[\\\\/:*?\"<>|]", " ").replaceAll("\\s+", " ").strip();
        if (limpo.isEmpty()) return "documento";
        return limpo.length() > 60 ? limpo.substring(0, 60).strip() : limpo;
    }
}
