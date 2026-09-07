package com.millenium.agente.adapter.wiki;

import com.millenium.agente.config.MilleniumProperties;
import com.millenium.agente.core.port.WikiPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Wiki em Markdown (tom, tratativas, principios, perguntas, parametros, governanca), consumida pela
 * tool consultar_wiki. Fonte confiavel e separada dos dados do cliente.
 * <p>
 * Ordem de carga: primeiro a <b>pasta externa editavel</b> {@code millenium.wiki-dir} (se existir e
 * tiver .md) - assim a equipe edita a wiki sem rebuild; caso contrario, a <b>wiki empacotada</b> no
 * jar ({@code classpath:wiki/}), que veio da pasta {@code wiki/} do projeto.
 */
@Component
public class MarkdownWikiAdapter implements WikiPort {

    private static final Logger log = LoggerFactory.getLogger(MarkdownWikiAdapter.class);

    private final Map<String, String> documentos = new LinkedHashMap<>();

    public MarkdownWikiAdapter(MilleniumProperties props) {
        carregar(props.getWikiDir());
    }

    private void carregar(String wikiDir) {
        if (carregarDoDisco(wikiDir)) {
            log.info("Wiki carregada da pasta externa {}: {} documento(s) {}", wikiDir, documentos.size(), documentos.keySet());
            return;
        }
        carregarDoClasspath();
        log.info("Wiki carregada do jar (classpath:wiki/): {} documento(s) {}", documentos.size(), documentos.keySet());
    }

    private boolean carregarDoDisco(String wikiDir) {
        if (wikiDir == null || wikiDir.isBlank()) return false;
        Path dir = Path.of(wikiDir);
        if (!Files.isDirectory(dir)) return false;
        Map<String, String> encontrados = new LinkedHashMap<>();
        try (Stream<Path> s = Files.walk(dir)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                if (Files.isRegularFile(p) && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".md")) {
                    encontrados.put(p.getFileName().toString(), Files.readString(p, StandardCharsets.UTF_8));
                }
            }
        } catch (IOException e) {
            log.warn("Falha ao ler a wiki externa {}: {}", wikiDir, e.getMessage());
            return false;
        }
        if (encontrados.isEmpty()) return false;
        documentos.putAll(encontrados);
        return true;
    }

    private void carregarDoClasspath() {
        try {
            var resolver = new PathMatchingResourcePatternResolver();
            Resource[] recursos = resolver.getResources("classpath*:wiki/**/*.md");
            for (Resource r : recursos) {
                String nome = r.getFilename();
                if (nome == null) continue;
                documentos.put(nome, new String(r.getContentAsByteArray(), StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            log.error("Falha ao carregar a wiki do jar: {}", e.getMessage(), e);
        }
    }

    @Override
    public List<String> listarDocumentos() {
        return List.copyOf(documentos.keySet());
    }

    @Override
    public Optional<String> buscarDocumento(String nome) {
        if (nome == null) return Optional.empty();
        String alvo = nome.toLowerCase(Locale.ROOT);
        return documentos.entrySet().stream()
                .filter(e -> e.getKey().toLowerCase(Locale.ROOT).equals(alvo)
                        || e.getKey().toLowerCase(Locale.ROOT).equals(alvo + ".md")
                        || e.getKey().toLowerCase(Locale.ROOT).contains(alvo))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    @Override
    public String buscar(String consulta) {
        if (consulta == null || consulta.isBlank()) {
            return "Documentos disponiveis na wiki: " + String.join(", ", listarDocumentos());
        }
        String[] termos = consulta.toLowerCase(Locale.ROOT).split("\\s+");
        List<String> achados = new ArrayList<>();
        for (var doc : documentos.entrySet()) {
            for (String paragrafo : doc.getValue().split("\\n\\s*\\n")) {
                String p = paragrafo.toLowerCase(Locale.ROOT);
                boolean casa = false;
                for (String t : termos) {
                    if (t.length() > 2 && p.contains(t)) {
                        casa = true;
                        break;
                    }
                }
                if (casa) {
                    achados.add("### " + doc.getKey() + "\n" + paragrafo.strip());
                    if (achados.size() >= 8) break;
                }
            }
            if (achados.size() >= 8) break;
        }
        if (achados.isEmpty()) {
            return "Nada encontrado na wiki para \"" + consulta + "\". Documentos: "
                    + String.join(", ", listarDocumentos());
        }
        return String.join("\n\n", achados);
    }
}
