/*
 * Instalador do Millenium Agente IA (MCP - Fase 1 PoC) para Windows, SEM PowerShell.
 *
 * Rode pelo lancador de 1 linha "instalar.cmd" (clique duplo ou terminal), que faz:
 *     java Installer.java [-SkipBuild] [-NoOpenFolder]
 *
 * Por que em Java? Voce ja tem o JDK 21. Como o proprio processo Java sabe o seu
 * java.home (System.getProperty), a deteccao do JDK vira trivial e resolve sozinha o
 * shim "javapath" da Oracle - o problema que travava a versao PowerShell. E o mesmo
 * java baixa o Maven (se faltar), compila, gera as pastas e escreve o
 * claude_desktop_config.json preservando os outros servidores MCP.
 *
 * O codigo esta escrito em Java 11-compativel de proposito: assim o instalador roda
 * mesmo que o "java" do PATH nao seja o 21 (nesse caso ele procura um JDK 21 nas pastas
 * comuns e usa esse para o build e para a config do Claude).
 *
 * Comentarios em portugues (padrao do projeto). Requisito unico da Fase 1: Java 21.
 */

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Installer {

    static final String MAVEN_VERSION = "3.9.9";
    static final String JAR_NAME = "millenium-agente-ia-0.1.0-SNAPSHOT.jar";

    // ------------------------------------------------------------ console ----
    static void step(String m) { System.out.println("\n==> " + m); }
    static void ok(String m)   { System.out.println("    [OK] " + m); }
    static void warn(String m) { System.out.println("    [!]  " + m); }
    static void info(String m) { System.out.println("    " + m); }
    static void line() { System.out.println("==================================================================="); }

    static void fail(String m) {
        System.out.println();
        System.out.println("[ERRO] " + m);
        System.exit(1);
    }

    // --------------------------------------------------------------- main ----
    public static void main(String[] args) throws Exception {
        List<String> a = Arrays.asList(args);
        boolean skipBuild = a.stream().anyMatch(s -> s.equalsIgnoreCase("-SkipBuild") || s.equalsIgnoreCase("--skip-build"));
        boolean noOpen    = a.stream().anyMatch(s -> s.equalsIgnoreCase("-NoOpenFolder") || s.equalsIgnoreCase("--no-open"));

        // A pasta do projeto e o diretorio de trabalho (o instalar.cmd faz "pushd %~dp0").
        // Aceita tambem um caminho posicional que contenha pom.xml, por seguranca.
        Path projectRoot = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (String s : args) {
            if (!s.startsWith("-") && Files.exists(Paths.get(s, "pom.xml"))) {
                projectRoot = Paths.get(s).toAbsolutePath().normalize();
                break;
            }
        }

        line();
        System.out.println("  Instalador - Millenium Agente IA (MCP Fase 1) - modo Java, sem PowerShell");
        System.out.println("  Pasta do projeto: " + projectRoot);
        System.out.println("  Requisito unico: Java 21 (Fase 1 le Excel local; SEM MySQL/banco).");
        line();

        if (!Files.exists(projectRoot.resolve("pom.xml"))) {
            fail("pom.xml nao encontrado em " + projectRoot + ". Rode o instalador de dentro da pasta do projeto.");
        }

        // alerta se estiver rodando de um lugar temporario (nao bloqueia)
        String rootStr = projectRoot.toString();
        if (rootStr.matches("(?i).*\\\\(Temp|Downloads|Download)\\\\.*")) {
            warn("A pasta parece temporaria (" + projectRoot + ").");
            warn("Mova o projeto para um local permanente (ex.: C:\\Millenium\\) e rode de novo,");
            warn("senao o caminho do jar na config do Claude vai quebrar depois.");
        }

        // 1) JDK 21 -------------------------------------------------------------
        step("Verificando JDK 21");
        String jdkHome = resolveJdk21();
        if (jdkHome == null) {
            fail("JDK 21 nao localizado. Se 'java --version' mostra 21 no seu terminal, rode o "
                 + "instalar.cmd nesse mesmo terminal. Senao instale o JDK 21 (Oracle/Temurin/Microsoft OpenJDK).");
        }
        Path javaExe = Paths.get(jdkHome, "bin", "java.exe");
        ok("JDK 21: " + jdkHome);

        // 2) Maven --------------------------------------------------------------
        step("Verificando Maven");
        String mvnToken = resolveMaven(projectRoot);
        ok("Maven: " + ("mvn".equals(mvnToken) ? "encontrado no PATH" : mvnToken));

        // 3) build --------------------------------------------------------------
        Path jarPath = projectRoot.resolve("target").resolve(JAR_NAME);
        if (skipBuild && Files.exists(jarPath)) {
            step("Build pulado (-SkipBuild); usando jar existente");
        } else {
            step("Compilando e empacotando (mvn clean package)");
            info("A primeira vez baixa as dependencias e pode demorar alguns minutos...");
            int code = run(projectRoot, jdkHome, mvnList(mvnToken, "-B", "clean", "package"));
            if (code != 0) fail("Falha no 'mvn clean package' (exit " + code + ").");
            if (!Files.exists(jarPath)) fail("Build terminou mas o jar nao foi gerado: " + jarPath);
            ok("Jar gerado: " + jarPath);
        }

        // 4) pastas NEUTRAS (previsiveis, independentes do caminho do projeto) --
        String userProfile = env("USERPROFILE");
        Path baseNeutra = Paths.get(userProfile, "MilleniumAgenteIA");
        Path fonteDir  = baseNeutra.resolve("fonte");
        Path wikiDir   = baseNeutra.resolve("wiki");
        Path outputDir = baseNeutra.resolve("saidas");

        step("Preparando a pasta de fontes (.xlsx): " + fonteDir);
        Files.createDirectories(fonteDir);
        if (hasFiles(fonteDir, ".xlsx")) {
            ok("Fontes ja presentes em: " + fonteDir);
        } else {
            List<Path> xlsx = listFiles(projectRoot.resolve("fonte"), ".xlsx");
            if (!xlsx.isEmpty()) {
                for (Path p : xlsx) {
                    Files.copy(p, fonteDir.resolve(p.getFileName().toString()), StandardCopyOption.REPLACE_EXISTING);
                }
                ok(xlsx.size() + " planilha(s) ficticia(s) copiada(s) para: " + fonteDir);
            } else {
                warn("Nenhum .xlsx para copiar. Coloque as 5 planilhas (Contatos, Sistema, Saidas por NF, Megazap, NPS) em: " + fonteDir);
            }
        }
        info("Para dados reais, substitua as 5 planilhas em " + fonteDir + " mantendo os nomes do field mapping");
        info("(N1 Contatos, N1 Sistema Contratado, N1 Saidas por NF, Megazap, NPS). Nenhum banco de dados e necessario.");

        step("Preparando a pasta da wiki (editavel): " + wikiDir);
        Files.createDirectories(wikiDir);
        Path origemWiki = projectRoot.resolve("wiki");
        if (hasFilesRecursive(wikiDir, ".md")) {
            ok("Wiki ja presente em: " + wikiDir);
        } else if (Files.isDirectory(origemWiki)) {
            copyTree(origemWiki, wikiDir);
            ok("Wiki copiada para: " + wikiDir + " (edite os .md aqui, sem rebuild)");
        } else {
            warn("Pasta wiki do projeto nao encontrada; o MCP usara a wiki empacotada no jar.");
        }
        Files.createDirectories(outputDir);

        // 5) config do Claude Desktop ------------------------------------------
        step("Registrando o MCP no Claude Desktop");
        Path claudeDir  = Paths.get(env("APPDATA"), "Claude");
        Path configPath = claudeDir.resolve("claude_desktop_config.json");
        if (!Files.isDirectory(claudeDir)) {
            warn("Pasta do Claude Desktop nao existe (" + claudeDir + ").");
            warn("O Claude Desktop parece nao estar instalado. Vou criar a config mesmo assim;");
            warn("instale o Claude Desktop depois e a config ja estara pronta.");
            Files.createDirectories(claudeDir);
        }

        Json.Obj cfg;
        if (Files.exists(configPath)) {
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Path backup = Paths.get(configPath + ".bak-" + stamp);
            Files.copy(configPath, backup, StandardCopyOption.REPLACE_EXISTING);
            info("Backup da config atual: " + backup);

            String raw = Files.readString(configPath, StandardCharsets.UTF_8);
            if (!raw.isEmpty() && raw.charAt(0) == '\uFEFF') raw = raw.substring(1); // tolera BOM na leitura
            Object parsed;
            try {
                parsed = Json.parse(raw);
            } catch (RuntimeException e) {
                fail("O arquivo " + configPath + " existe mas nao e um JSON valido. Corrija ou remova antes de rodar. (" + e.getMessage() + ")");
                return;
            }
            if (!(parsed instanceof Json.Obj)) {
                fail("A config do Claude nao e um objeto JSON no topo.");
                return;
            }
            cfg = (Json.Obj) parsed;
        } else {
            cfg = new Json.Obj();
        }

        // garante o objeto mcpServers sem apagar servidores existentes
        Object serversNode = cfg.get("mcpServers");
        Json.Obj mcpServers;
        if (serversNode instanceof Json.Obj) {
            mcpServers = (Json.Obj) serversNode;
        } else {
            mcpServers = new Json.Obj();
            cfg.put("mcpServers", mcpServers);
        }

        // entrada do nosso servidor (ordem: command, args)
        Json.Obj entry = new Json.Obj();
        entry.put("command", javaExe.toString());
        Json.Arr argv = new Json.Arr();
        argv.add("-jar");
        argv.add(jarPath.toString());
        argv.add("--millenium.fonte-dir=" + fonteDir);
        argv.add("--millenium.wiki-dir=" + wikiDir);
        argv.add("--millenium.output-dir=" + outputDir);
        entry.put("args", argv);

        // adiciona/atualiza SO a chave do nosso servidor (LinkedHashMap mantem a posicao)
        mcpServers.put("millenium-agente-ia", entry);

        // grava UTF-8 SEM BOM (o parser do Claude nao aceita BOM)
        Files.write(configPath, Json.write(cfg).getBytes(StandardCharsets.UTF_8));
        ok("Config atualizada: " + configPath);
        info("command: " + javaExe);
        info("jar    : " + jarPath);

        // 6) Instruction / Skills (parte manual no Projeto do Claude) ----------
        Path agentesDir = projectRoot.resolve("agentes");
        step("Skills e Instruction (para colar no Projeto do Claude)");
        info("Instruction (regras/LGPD): " + agentesDir.resolve("instrucao-guardrails.md"));
        info("Skill Preventivo        : " + agentesDir.resolve("preventivo").resolve("SKILL.md"));
        info("Skill Pos-NPS           : " + agentesDir.resolve("pos-nps").resolve("SKILL.md"));
        info("Skill Exportar cadastro : " + agentesDir.resolve("exportar-cadastro").resolve("SKILL.md") + "  (gatilho: 'gerar excel')");
        info("A wiki NAO precisa instalar: o agente le pela tool consultar_wiki.");

        // --- fim ---
        System.out.println();
        line();
        System.out.println("  INSTALACAO CONCLUIDA");
        line();
        System.out.println(
                "\nProximos passos (manuais, no app do Claude):\n" +
                "  1. FECHE o Claude Desktop pela bandeja do sistema (botao direito -> Sair) e abra de novo.\n" +
                "  2. Confirme que 'millenium-agente-ia' aparece conectado com 11 tools.\n" +
                "  3. Crie/abra um Projeto no Claude e:\n" +
                "       - cole o conteudo de agentes\\instrucao-guardrails.md nas Instrucoes do Projeto;\n" +
                "       - cadastre as Skills em agentes\\preventivo\\SKILL.md e agentes\\pos-nps\\SKILL.md.\n" +
                "  4. Teste no chat: \"chame a tool preventivo_contatos\".\n" +
                "\nLog do MCP (se precisar depurar):\n" +
                "  %APPDATA%\\Claude\\logs\\mcp-server-millenium-agente-ia.log");

        if (!noOpen && Files.isDirectory(agentesDir)) {
            try {
                new ProcessBuilder("cmd", "/c", "start", "", agentesDir.toString()).start();
            } catch (Exception ignore) { /* abrir o Explorer e opcional */ }
        }
    }

    // ------------------------------------------------------- deteccao JDK ----
    /** Devolve o JAVA_HOME de um JDK >= 21, ou null. */
    static String resolveJdk21() {
        // Caso comum: o proprio processo ja roda em 21 (via 'java' do PATH / shim javapath).
        // System.getProperty("java.home") entrega o home REAL, mesmo lancado pelo shim.
        int feature = Runtime.version().feature();
        String home = System.getProperty("java.home");
        if (feature >= 21 && home != null && Files.exists(Paths.get(home, "bin", "java.exe"))) {
            return home;
        }
        // Fallback: o instalador esta rodando num Java mais antigo. Procura um 21 instalado.
        return huntJdk21();
    }

    static String huntJdk21() {
        List<String> candidates = new ArrayList<>();
        String javaHomeEnv = System.getenv("JAVA_HOME");
        if (javaHomeEnv != null && !javaHomeEnv.isEmpty()) candidates.add(javaHomeEnv);

        String pf    = env0("ProgramFiles");
        String pf86  = env0("ProgramFiles(x86)");
        String local = env0("LOCALAPPDATA");
        String userp = env0("USERPROFILE");
        String[] roots = {
                join(pf, "Microsoft"), join(pf, "Eclipse Adoptium"), join(pf, "Java"),
                join(pf, "Zulu"), join(pf, "Amazon Corretto"), join(pf, "BellSoft"),
                join(pf, "Semeru"), join(pf86, "Java"),
                join(local, "Programs", "Eclipse Adoptium"), join(userp, ".jdks")
        };
        for (String r : roots) {
            if (r == null) continue;
            Path rp = Paths.get(r);
            if (!Files.isDirectory(rp)) continue;
            try (Stream<Path> s = Files.list(rp)) {
                s.filter(Files::isDirectory)
                 .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
                 .forEach(p -> candidates.add(p.toString()));
            } catch (IOException ignore) { }
        }
        for (String c : candidates) {
            Path exe = Paths.get(c, "bin", "java.exe");
            if (Files.exists(exe) && javaMajorOf(exe.toString()) >= 21) return c;
        }
        // por ultimo, o java do PATH (resolve shim via -XshowSettings)
        String where = firstLine(capture(Arrays.asList("cmd", "/c", "where", "java")));
        if (where != null && Files.exists(Paths.get(where))) {
            String[] props = javaProps(where);
            String jh = props[0];
            int mj = props[1] == null ? 0 : Integer.parseInt(props[1]);
            if (mj >= 21 && jh != null && Files.exists(Paths.get(jh, "bin", "java.exe"))) return jh;
        }
        return null;
    }

    /** Roda "<java> -XshowSettings:properties -version" e devolve {java.home, majorVersion}. */
    static String[] javaProps(String javaExe) {
        String out = capture(Arrays.asList(javaExe, "-XshowSettings:properties", "-version"));
        String home = null, major = null;
        Matcher mh = Pattern.compile("java\\.home\\s*=\\s*(.+)").matcher(out);
        if (mh.find()) home = mh.group(1).trim();
        Matcher mv = Pattern.compile("java\\.version\\s*=\\s*\"?(\\d+)(?:\\.(\\d+))?").matcher(out);
        if (mv.find()) {
            int mj = Integer.parseInt(mv.group(1));
            if (mj == 1 && mv.group(2) != null) mj = Integer.parseInt(mv.group(2)); // 1.8 -> 8
            major = Integer.toString(mj);
        }
        return new String[]{home, major};
    }

    static int javaMajorOf(String javaExe) {
        String m = javaProps(javaExe)[1];
        return m == null ? 0 : Integer.parseInt(m);
    }

    // ----------------------------------------------------------- Maven -------
    /** Devolve "mvn" (usar o do PATH) ou o caminho completo do mvn.cmd baixado. */
    static String resolveMaven(Path projectRoot) throws Exception {
        String where = firstLine(capture(Arrays.asList("cmd", "/c", "where", "mvn")));
        if (where != null && Files.exists(Paths.get(where))) return "mvn";

        Path toolsDir = projectRoot.resolve(".tools");
        Path mvnHome  = toolsDir.resolve("apache-maven-" + MAVEN_VERSION);
        Path mvnCmd   = mvnHome.resolve("bin").resolve("mvn.cmd");
        if (Files.exists(mvnCmd)) return mvnCmd.toString();

        warn("Maven nao encontrado. Baixando Apache Maven " + MAVEN_VERSION + " (uma vez)...");
        Files.createDirectories(toolsDir);
        Path zip = toolsDir.resolve("apache-maven-" + MAVEN_VERSION + "-bin.zip");
        String[] urls = {
                "https://dlcdn.apache.org/maven/maven-3/" + MAVEN_VERSION + "/binaries/apache-maven-" + MAVEN_VERSION + "-bin.zip",
                "https://archive.apache.org/dist/maven/maven-3/" + MAVEN_VERSION + "/binaries/apache-maven-" + MAVEN_VERSION + "-bin.zip"
        };
        boolean downloaded = false;
        for (String u : urls) {
            try {
                info("baixando de " + u);
                download(u, zip);
                downloaded = true;
                break;
            } catch (Exception e) {
                warn("falhou nesse espelho, tentando o proximo...");
            }
        }
        if (!downloaded) throw new IOException("Nao consegui baixar o Maven. Verifique a internet/proxy.");

        unzip(zip, toolsDir);
        Files.deleteIfExists(zip);
        if (!Files.exists(mvnCmd)) throw new IOException("Maven extraido mas mvn.cmd nao encontrado em " + mvnCmd);
        ok("Maven baixado em: " + mvnHome);
        return mvnCmd.toString();
    }

    static List<String> mvnList(String mvnToken, String... goals) {
        List<String> c = new ArrayList<>();
        c.add("cmd");
        c.add("/c");
        c.add(mvnToken);              // "mvn" (PATH) ou caminho do mvn.cmd
        c.addAll(Arrays.asList(goals));
        return c;
    }

    // --------------------------------------------------------- processos ----
    /** Roda um comando mostrando a saida ao vivo; injeta JAVA_HOME/Path do JDK 21. */
    static int run(Path dir, String javaHome, List<String> cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(dir.toFile()).inheritIO();
        pb.environment().put("JAVA_HOME", javaHome);
        String path = System.getenv("Path");
        pb.environment().put("Path", Paths.get(javaHome, "bin") + File.pathSeparator + (path == null ? "" : path));
        return pb.start().waitFor();
    }

    /** Roda um comando e devolve stdout+stderr como texto (para deteccao). */
    static String capture(List<String> cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            p.waitFor();
            return out;
        } catch (Exception e) {
            return "";
        }
    }

    // ------------------------------------------------------- rede / zip -----
    static void download(String url, Path dest) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<Path> resp = client.send(req, HttpResponse.BodyHandlers.ofFile(dest));
        if (resp.statusCode() != 200) throw new IOException("HTTP " + resp.statusCode());
    }

    static void unzip(Path zip, Path destDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                Path out = destDir.resolve(e.getName()).normalize();
                if (!out.startsWith(destDir)) throw new IOException("Entrada de zip suspeita (zip slip): " + e.getName());
                if (e.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(zis, out, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    // -------------------------------------------------------- utilitarios ---
    static String env(String name) {
        String v = System.getenv(name);
        if (v == null || v.isEmpty()) fail("Variavel de ambiente " + name + " nao definida.");
        return v;
    }

    static String env0(String name) {
        String v = System.getenv(name);
        return (v == null || v.isEmpty()) ? null : v;
    }

    static String join(String base, String... parts) {
        if (base == null) return null;
        return Paths.get(base, parts).toString();
    }

    static String firstLine(String s) {
        if (s == null) return null;
        for (String ln : s.split("\\r?\\n")) {
            String t = ln.trim();
            if (!t.isEmpty()) return t;
        }
        return null;
    }

    static boolean hasFiles(Path dir, String ext) {
        return !listFiles(dir, ext).isEmpty();
    }

    static List<Path> listFiles(Path dir, String ext) {
        if (!Files.isDirectory(dir)) return new ArrayList<>();
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(ext.toLowerCase()))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    static boolean hasFilesRecursive(Path dir, String ext) {
        if (!Files.isDirectory(dir)) return false;
        try (Stream<Path> s = Files.walk(dir)) {
            return s.anyMatch(p -> Files.isRegularFile(p)
                    && p.getFileName().toString().toLowerCase().endsWith(ext.toLowerCase()));
        } catch (IOException e) {
            return false;
        }
    }

    static void copyTree(Path src, Path dst) throws IOException {
        try (Stream<Path> s = Files.walk(src)) {
            List<Path> all = s.collect(Collectors.toList());
            for (Path p : all) {
                Path target = dst.resolve(src.relativize(p).toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(p, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    // ==========================================================================
    //  Mini-toolkit JSON (sem dependencias): parse -> Obj/Arr/Num/String/Bool/null
    //  e writer pretty com 2 espacos. Preserva ordem das chaves (LinkedHashMap) e
    //  os literais numericos originais (nao troca 3 por 3.0). Suficiente e seguro
    //  para mesclar o claude_desktop_config.json sem apagar outros servidores.
    // ==========================================================================
    static final class Json {
        static final class Obj extends LinkedHashMap<String, Object> { }
        static final class Arr extends ArrayList<Object> { }
        static final class Num {
            final String raw;
            Num(String r) { this.raw = r; }
        }

        // ---- parse ----
        static Object parse(String s) {
            int[] i = {0};
            skipWs(s, i);
            Object v = parseValue(s, i);
            skipWs(s, i);
            if (i[0] != s.length()) throw new RuntimeException("conteudo extra apos o JSON na posicao " + i[0]);
            return v;
        }

        static void skipWs(String s, int[] i) {
            while (i[0] < s.length() && Character.isWhitespace(s.charAt(i[0]))) i[0]++;
        }

        static Object parseValue(String s, int[] i) {
            if (i[0] >= s.length()) throw new RuntimeException("fim inesperado do JSON");
            char c = s.charAt(i[0]);
            switch (c) {
                case '{': return parseObj(s, i);
                case '[': return parseArr(s, i);
                case '"': return parseStr(s, i);
                case 't': expect(s, i, "true");  return Boolean.TRUE;
                case 'f': expect(s, i, "false"); return Boolean.FALSE;
                case 'n': expect(s, i, "null");  return null;
                default:  return parseNum(s, i);
            }
        }

        static Obj parseObj(String s, int[] i) {
            Obj o = new Obj();
            i[0]++; // {
            skipWs(s, i);
            if (s.charAt(i[0]) == '}') { i[0]++; return o; }
            while (true) {
                skipWs(s, i);
                if (s.charAt(i[0]) != '"') throw new RuntimeException("esperava uma chave string na posicao " + i[0]);
                String k = parseStr(s, i);
                skipWs(s, i);
                if (s.charAt(i[0]) != ':') throw new RuntimeException("esperava ':' na posicao " + i[0]);
                i[0]++;
                skipWs(s, i);
                o.put(k, parseValue(s, i));
                skipWs(s, i);
                char c = s.charAt(i[0]);
                if (c == ',') { i[0]++; continue; }
                if (c == '}') { i[0]++; break; }
                throw new RuntimeException("esperava ',' ou '}' na posicao " + i[0]);
            }
            return o;
        }

        static Arr parseArr(String s, int[] i) {
            Arr arr = new Arr();
            i[0]++; // [
            skipWs(s, i);
            if (s.charAt(i[0]) == ']') { i[0]++; return arr; }
            while (true) {
                skipWs(s, i);
                arr.add(parseValue(s, i));
                skipWs(s, i);
                char c = s.charAt(i[0]);
                if (c == ',') { i[0]++; continue; }
                if (c == ']') { i[0]++; break; }
                throw new RuntimeException("esperava ',' ou ']' na posicao " + i[0]);
            }
            return arr;
        }

        static String parseStr(String s, int[] i) {
            StringBuilder sb = new StringBuilder();
            i[0]++; // "
            while (true) {
                if (i[0] >= s.length()) throw new RuntimeException("string sem fechamento");
                char c = s.charAt(i[0]++);
                if (c == '"') break;
                if (c == '\\') {
                    char e = s.charAt(i[0]++);
                    switch (e) {
                        case '"':  sb.append('"');  break;
                        case '\\': sb.append('\\'); break;
                        case '/':  sb.append('/');  break;
                        case 'b':  sb.append('\b'); break;
                        case 'f':  sb.append('\f'); break;
                        case 'n':  sb.append('\n'); break;
                        case 'r':  sb.append('\r'); break;
                        case 't':  sb.append('\t'); break;
                        case 'u':  sb.append((char) Integer.parseInt(s.substring(i[0], i[0] + 4), 16)); i[0] += 4; break;
                        default: throw new RuntimeException("escape invalido \\" + e);
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        static Num parseNum(String s, int[] i) {
            int start = i[0];
            while (i[0] < s.length() && "+-0123456789.eE".indexOf(s.charAt(i[0])) >= 0) i[0]++;
            String tok = s.substring(start, i[0]);
            if (tok.isEmpty()) throw new RuntimeException("valor invalido na posicao " + start);
            return new Num(tok);
        }

        static void expect(String s, int[] i, String word) {
            if (!s.regionMatches(i[0], word, 0, word.length()))
                throw new RuntimeException("esperava '" + word + "' na posicao " + i[0]);
            i[0] += word.length();
        }

        // ---- write (pretty, 2 espacos) ----
        static String write(Object v) {
            StringBuilder sb = new StringBuilder();
            writeVal(v, sb, 0);
            sb.append('\n');
            return sb.toString();
        }

        static void indent(StringBuilder sb, int n) {
            for (int k = 0; k < n; k++) sb.append("  ");
        }

        static void writeVal(Object v, StringBuilder sb, int lvl) {
            if (v == null) { sb.append("null"); return; }
            if (v instanceof Obj) {
                Obj o = (Obj) v;
                if (o.isEmpty()) { sb.append("{}"); return; }
                sb.append("{\n");
                int n = o.size(), k = 0;
                for (Map.Entry<String, Object> en : o.entrySet()) {
                    indent(sb, lvl + 1);
                    sb.append('"').append(esc(en.getKey())).append("\": ");
                    writeVal(en.getValue(), sb, lvl + 1);
                    if (++k < n) sb.append(',');
                    sb.append('\n');
                }
                indent(sb, lvl);
                sb.append('}');
            } else if (v instanceof Arr) {
                Arr arr = (Arr) v;
                if (arr.isEmpty()) { sb.append("[]"); return; }
                sb.append("[\n");
                int n = arr.size();
                for (int idx = 0; idx < n; idx++) {
                    indent(sb, lvl + 1);
                    writeVal(arr.get(idx), sb, lvl + 1);
                    if (idx < n - 1) sb.append(',');
                    sb.append('\n');
                }
                indent(sb, lvl);
                sb.append(']');
            } else if (v instanceof Num) {
                sb.append(((Num) v).raw);
            } else if (v instanceof Boolean) {
                sb.append(v.toString());
            } else {
                sb.append('"').append(esc(v.toString())).append('"');
            }
        }

        static String esc(String s) {
            StringBuilder b = new StringBuilder();
            for (int k = 0; k < s.length(); k++) {
                char c = s.charAt(k);
                switch (c) {
                    case '"':  b.append("\\\""); break;
                    case '\\': b.append("\\\\"); break;
                    case '\n': b.append("\\n");  break;
                    case '\r': b.append("\\r");  break;
                    case '\t': b.append("\\t");  break;
                    case '\b': b.append("\\b");  break;
                    case '\f': b.append("\\f");  break;
                    default:
                        if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                        else b.append(c);
                }
            }
            return b.toString();
        }
    }
}
