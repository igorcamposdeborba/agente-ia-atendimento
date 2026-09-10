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

    private final Map<String, String> documents = new LinkedHashMap<>();

    public MarkdownWikiAdapter(MilleniumProperties props) {
        load(props.getWikiDir());
    }

    private void load(String wikiDir) {
        if (loadFromDisk(wikiDir)) {
            log.info("Wiki carregada da pasta externa {}: {} documento(s) {}", wikiDir, documents.size(), documents.keySet());
            return;
        }
        loadFromClasspath();
        log.info("Wiki carregada do jar (classpath:wiki/): {} documento(s) {}", documents.size(), documents.keySet());
    }

    private boolean loadFromDisk(String wikiDir) {
        if (wikiDir == null || wikiDir.isBlank()) return false;
        Path dir = Path.of(wikiDir);
        if (!Files.isDirectory(dir)) return false;
        Map<String, String> found = new LinkedHashMap<>();
        try (Stream<Path> s = Files.walk(dir)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                if (Files.isRegularFile(p) && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".md")) {
                    found.put(p.getFileName().toString(), Files.readString(p, StandardCharsets.UTF_8));
                }
            }
        } catch (IOException e) {
            log.warn("Falha ao ler a wiki externa {}: {}", wikiDir, e.getMessage());
            return false;
        }
        if (found.isEmpty()) return false;
        documents.putAll(found);
        return true;
    }

    private void loadFromClasspath() {
        try {
            var resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath*:wiki/**/*.md");
            for (Resource r : resources) {
                String name = r.getFilename();
                if (name == null) continue;
                documents.put(name, new String(r.getContentAsByteArray(), StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            log.error("Falha ao carregar a wiki do jar: {}", e.getMessage(), e);
        }
    }

    @Override
    public List<String> listDocuments() {
        return List.copyOf(documents.keySet());
    }

    @Override
    public Optional<String> findDocument(String name) {
        if (name == null) return Optional.empty();
        String target = name.toLowerCase(Locale.ROOT);
        return documents.entrySet().stream()
                .filter(e -> e.getKey().toLowerCase(Locale.ROOT).equals(target)
                        || e.getKey().toLowerCase(Locale.ROOT).equals(target + ".md")
                        || e.getKey().toLowerCase(Locale.ROOT).contains(target))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    @Override
    public String search(String query) {
        if (query == null || query.isBlank()) {
            return "Documentos disponiveis na wiki: " + String.join(", ", listDocuments());
        }
        String[] terms = query.toLowerCase(Locale.ROOT).split("\\s+");
        List<String> matches = new ArrayList<>();
        for (var doc : documents.entrySet()) {
            for (String paragraph : doc.getValue().split("\\n\\s*\\n")) {
                String p = paragraph.toLowerCase(Locale.ROOT);
                boolean matched = false;
                for (String t : terms) {
                    if (t.length() > 2 && p.contains(t)) {
                        matched = true;
                        break;
                    }
                }
                if (matched) {
                    matches.add("### " + doc.getKey() + "\n" + paragraph.strip());
                    if (matches.size() >= 8) break;
                }
            }
            if (matches.size() >= 8) break;
        }
        if (matches.isEmpty()) {
            return "Nada encontrado na wiki para \"" + query + "\". Documentos: "
                    + String.join(", ", listDocuments());
        }
        return String.join("\n\n", matches);
    }
}
