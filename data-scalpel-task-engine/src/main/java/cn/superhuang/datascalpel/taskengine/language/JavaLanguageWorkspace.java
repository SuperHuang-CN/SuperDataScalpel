package cn.superhuang.datascalpel.taskengine.language;

import cn.superhuang.datascalpel.taskengine.compiler.SparkJarOnlineSourceCompiler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

/** One independent Eclipse process and a single managed Java document; never a user project importer. */
final class JavaLanguageWorkspace implements AutoCloseable {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> METHODS = Set.of("initialize", "initialized", "$/cancelRequest",
            "textDocument/didOpen", "textDocument/didChange", "textDocument/didClose",
            "textDocument/completion", "completionItem/resolve", "textDocument/hover",
            "textDocument/signatureHelp", "textDocument/formatting", "textDocument/rename", "textDocument/documentHighlight", "textDocument/documentSymbol");
    private static final String VIRTUAL_ROOT = "file:///datascalpel/";
    private final LanguageServiceConfiguration config;
    private final Function<Path, Path> indexes;
    private final Path root;
    private final Path project;
    private final String projectUri;
    private final ThreadPoolExecutor input = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(32), Thread.ofPlatform().daemon().name("java-language-input").factory());
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile Process process;
    private volatile Consumer<String> output;
    private volatile Instant disconnectedAt;
    private volatile JsonNode initializationResult;
    private volatile String serviceReadyNotification;
    private boolean initializing;
    private String documentUri;
    private int version = -1;
    private final Instant createdAt = Instant.now();

    JavaLanguageWorkspace(LanguageServiceConfiguration config) throws IOException {
        this(config, root -> null);
    }

    JavaLanguageWorkspace(LanguageServiceConfiguration config, Function<Path, Path> indexes) throws IOException {
        this.config = config;
        this.indexes = indexes;
        Files.createDirectories(config.workRoot());
        root = Files.createTempDirectory(config.workRoot(), "session-");
        project = root.resolve("project");
        Files.createDirectories(project);
        // Path.toUri() probes the filesystem to decide whether to append '/'.
        // Compute the stable directory prefix once, not for every LSP JSON string/key.
        projectUri = project.toUri().toString();
    }

    synchronized boolean attach(Consumer<String> sink) {
        if (closed.get() || output != null) return false;
        output = sink;
        disconnectedAt = null;
        return true;
    }

    synchronized void detach() {
        output = null;
        disconnectedAt = Instant.now();
    }

    boolean expired(Instant now) {
        Instant since = disconnectedAt;
        return closed.get() || initializationResult == null && createdAt.plusSeconds(120).isBefore(now)
                || since != null && since.plus(config.retention()).isBefore(now);
    }

    void accept(String raw) {
        input.execute(() -> {
            if (closed.get()) return;
            try { receive(raw); }
            catch (Exception ex) { fail("JAVA_LANGUAGE_PROTOCOL_FAILED", "Java 提示连接失败，请重新连接"); }
        });
    }

    private void receive(String raw) throws Exception {
        JsonNode parsed = JSON.readTree(raw);
        if (!(parsed instanceof ObjectNode message) || !message.path("jsonrpc").asText().equals("2.0"))
            throw new IOException("Invalid JSON RPC");
        String method = message.path("method").asText();
        if (!METHODS.contains(method)) throw new IOException("Unsupported language operation");
        if (method.equals("initialize")) {
            if (initializationResult != null) {
                emit(JSON.createObjectNode().put("jsonrpc", "2.0").set("id", message.get("id")), initializationResult);
                if (serviceReadyNotification != null) send(serviceReadyNotification);
                return;
            }
            if (initializing) throw new IOException("Already initializing");
            initializing = true;
            start();
            ObjectNode params = message.putObject("params");
            params.put("processId", ProcessHandle.current().pid());
            params.put("rootUri", projectUri);
            params.putArray("workspaceFolders").addObject().put("uri", projectUri).put("name", "job");
            params.set("capabilities", JSON.readTree("""
                    {"textDocument":{"completion":{"completionItem":{"snippetSupport":true,"documentationFormat":["markdown","plaintext"],"resolveSupport":{"properties":["documentation","detail","additionalTextEdits"]}}},"hover":{"contentFormat":["markdown","plaintext"]},"signatureHelp":{"signatureInformation":{"documentationFormat":["markdown","plaintext"],"parameterInformation":{"labelOffsetSupport":true}}},"publishDiagnostics":{"versionSupport":true},"documentSymbol":{"hierarchicalDocumentSymbolSupport":true}}}
                    """));
            ObjectNode settings = params.putObject("initializationOptions").putObject("settings").putObject("java");
            settings.put("home", System.getProperty("java.home"));
            settings.putObject("import").putObject("maven").put("enabled", false);
            ((ObjectNode) settings.get("import")).putObject("gradle").put("enabled", false);
            settings.putObject("configuration").put("updateBuildConfiguration", "disabled");
            settings.putObject("autobuild").put("enabled", true);
            ObjectNode completion = settings.putObject("completion").put("maxResults", 100);
            // Set explicitly: JDT defaults alone do not install the Eclipse type-filter preference.
            // Keep public Java/Spark APIs; hide implementation-only classes from discovery.
            completion.putArray("filteredTypes").add("com.sun.*").add("sun.*").add("jdk.*")
                    .add("io.micrometer.shaded.*").add("org.graalvm.*");
            settings.putObject("signatureHelp").put("enabled", true);
            write(message);
            return;
        }
        if (process == null || !process.isAlive()) throw new IOException("Language process unavailable");
        if (method.equals("initialized")) {
            // Reconnected clients initialize again; Eclipse itself is initialized only once.
            if (!initializing) return;
            initializing = false;
        }
        if (method.startsWith("textDocument/")) {
            JsonNode doc = message.path("params").path("textDocument");
            String uri = doc.path("uri").asText();
            if (!method.equals("textDocument/formatting") || !uri.isEmpty()) validateUri(uri);
            if (method.equals("textDocument/didOpen")) {
                String source = doc.path("text").asText();
                validateSource(source);
                String previousUri = documentUri;
                if (documentUri != null) write(JSON.createObjectNode().put("jsonrpc", "2.0")
                        .put("method", "textDocument/didClose").set("params", JSON.readTree(
                                "{\"textDocument\":{\"uri\":" + JSON.writeValueAsString(realUri(documentUri)) + "}}")));
                if (documentUri != null && !documentUri.equals(uri))
                    Files.deleteIfExists(project.resolve(documentUri.substring(VIRTUAL_ROOT.length())));
                documentUri = uri;
                version = doc.path("version").asInt(-1);
                Path file = project.resolve(uri.substring(VIRTUAL_ROOT.length()));
                Files.createDirectories(file.getParent());
                Files.writeString(file, source);
                if (previousUri != null && !previousUri.equals(uri)) {
                    // Closing a working copy does not remove Eclipse's resource model.
                    // Report the managed file move so undo/reusing a name cannot see a phantom unit.
                    ObjectNode changed = JSON.createObjectNode().put("jsonrpc", "2.0")
                            .put("method", "workspace/didChangeWatchedFiles");
                    var events = changed.putObject("params").putArray("changes");
                    events.addObject().put("uri", realUri(previousUri)).put("type", 3); // Deleted
                    events.addObject().put("uri", realUri(uri)).put("type", 1); // Created
                    write(changed);
                }
            } else if (!uri.equals(documentUri)) throw new IOException("Document is not open");
            if (method.equals("textDocument/didChange")) {
                int next = doc.path("version").asInt(-1);
                if (next <= version) return;
                JsonNode changes = message.path("params").path("contentChanges");
                if (!changes.isArray() || changes.size() != 1 || changes.get(0).has("range"))
                    throw new IOException("Full document synchronization required");
                validateSource(changes.get(0).path("text").asText());
                version = next;
            }
            if (method.equals("textDocument/didClose")) documentUri = null;
        }
        write(rewrite(message, true));
    }

    static void validateSource(String source) throws IOException {
        if (source.indexOf('\0') >= 0 || source.getBytes(StandardCharsets.UTF_8).length > 256 * 1024)
            throw new IOException("Source too large");
    }

    static void validateUri(String uri) throws IOException {
        if (!uri.matches("file:///datascalpel/src/(?:[A-Za-z_$][A-Za-z0-9_$]*/)*[A-Za-z_$][A-Za-z0-9_$]*\\.java"))
            throw new IOException("Only a managed Java source is allowed");
    }

    // validateUri restricts the relative part to URI-safe ASCII Java path segments.
    private String realUri(String uri) { return projectUri + uri.substring(VIRTUAL_ROOT.length()); }

    private JsonNode rewrite(JsonNode value, boolean inbound) throws IOException {
        if (value.isTextual()) {
            String text = value.asText();
            if (inbound && text.startsWith(VIRTUAL_ROOT)) {
                validateUri(text);
                return JSON.getNodeFactory().textNode(realUri(text));
            }
            if (!inbound && text.startsWith(projectUri))
                return JSON.getNodeFactory().textNode(VIRTUAL_ROOT + text.substring(projectUri.length()));
            return value;
        }
        if (value.isObject()) {
            ObjectNode copy = JSON.createObjectNode();
            var fields = value.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                // WorkspaceEdit.changes uses document URIs as object keys, not only values.
                String key = rewrite(JSON.getNodeFactory().textNode(field.getKey()), inbound).asText();
                copy.set(key, rewrite(field.getValue(), inbound));
            }
            return copy;
        }
        if (value.isArray()) {
            var copy = JSON.createArrayNode();
            for (JsonNode item : value) copy.add(rewrite(item, inbound));
            return copy;
        }
        return value;
    }

    private void start() throws IOException {
        if (config.home() == null || !Files.isDirectory(config.home())) throw new IOException("JDT LS not installed");
        List<Path> libraries = SparkJarOnlineSourceCompiler.compilerClasspath();
        if (libraries.isEmpty()) throw new IOException("Missing public analysis dependencies");
        Files.createDirectories(project.resolve("src"));
        Files.createDirectories(project.resolve(".settings"));
        Files.writeString(project.resolve(".project"), """
                <projectDescription><name>job</name><comment/><projects/><buildSpec><buildCommand><name>org.eclipse.jdt.core.javabuilder</name><arguments/></buildCommand></buildSpec><natures><nature>org.eclipse.jdt.core.javanature</nature></natures></projectDescription>
                """);
        StringBuilder cp = new StringBuilder("<classpath><classpathentry kind=\"src\" path=\"src\"/><classpathentry kind=\"con\" path=\"org.eclipse.jdt.launching.JRE_CONTAINER/org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/JavaSE-21\"/>");
        for (Path lib : libraries) cp.append("<classpathentry kind=\"lib\" path=\"").append(xml(lib.toString().replace('\\', '/'))).append("\"/>");
        Files.writeString(project.resolve(".classpath"), cp.append("<classpathentry kind=\"output\" path=\"bin\"/></classpath>").toString());
        Files.writeString(project.resolve(".settings/org.eclipse.jdt.core.prefs"), """
                eclipse.preferences.version=1
                org.eclipse.jdt.core.compiler.compliance=21
                org.eclipse.jdt.core.compiler.source=21
                org.eclipse.jdt.core.compiler.codegen.targetPlatform=21
                org.eclipse.jdt.core.compiler.processAnnotations=disabled
                """);
        Path launcher;
        try (var plugins = Files.list(config.home().resolve("plugins"))) {
            launcher = plugins.filter(p -> p.getFileName().toString().startsWith("org.eclipse.equinox.launcher_"))
                    .filter(p -> p.toString().endsWith(".jar")).findFirst().orElseThrow(() -> new IOException("Missing JDT launcher"));
        }
        String os = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT);
        Path originalConfig = config.home().resolve(runtimeConfigDirectory());
        Path eclipseConfig = root.resolve("config");
        try (var paths = Files.walk(originalConfig)) {
            for (Path from : paths.toList()) {
                Path to = eclipseConfig.resolve(originalConfig.relativize(from));
                if (Files.isDirectory(from)) Files.createDirectories(to); else Files.copy(from, to);
            }
        }
        List<String> command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", os.contains("win") ? "java.exe" : "java").toString(),
                "-Xms128m", "-Xmx" + config.heapMiB() + "m", "-XX:ActiveProcessorCount=2",
                "-Declipse.application=org.eclipse.jdt.ls.core.id1", "-Dosgi.bundles.defaultStartLevel=4",
                "-Declipse.product=org.eclipse.jdt.ls.core.product", "-Dlog.level=WARNING", "--add-modules=ALL-SYSTEM",
                "--add-opens", "java.base/java.util=ALL-UNNAMED", "--add-opens", "java.base/java.lang=ALL-UNNAMED",
                "-jar", launcher.toString(), "-configuration", eclipseConfig.toString(), "-data", root.resolve("metadata").toString()));
        Path indexLocation = indexes.apply(root);
        if (indexLocation != null) command.add(1, "-Djdt.core.sharedIndexLocation=" + indexLocation.toAbsolutePath());
        ProcessBuilder builder = new ProcessBuilder(command).directory(config.home().toFile()).redirectError(ProcessBuilder.Redirect.DISCARD);
        builder.environment().remove("JAVA_TOOL_OPTIONS");
        builder.environment().remove("JDK_JAVA_OPTIONS");
        synchronized (this) {
            if (closed.get()) throw new IOException("Language session closed during initialization");
            process = builder.start();
        }
        Thread.ofVirtual().name("java-language-output").start(() -> {
            try {
                byte[] bytes;
                while ((bytes = LspFrames.read(process.getInputStream())) != null && !closed.get()) {
                    ObjectNode message = (ObjectNode) JSON.readTree(bytes);
                    if (message.has("method") && message.has("id")) {
                        ObjectNode reply = JSON.createObjectNode().put("jsonrpc", "2.0");
                        reply.set("id", message.get("id"));
                        if (message.path("method").asText().equals("workspace/applyEdit")) reply.putObject("result").put("applied", false);
                        else reply.putNull("result");
                        write(reply);
                    } else {
                        if (message.path("result").has("capabilities")) initializationResult = message.get("result");
                        String outgoing = JSON.writeValueAsString(rewrite(message, false));
                        if (message.path("method").asText().equals("language/status")
                                && message.path("params").path("type").asText().equals("ServiceReady"))
                            serviceReadyNotification = outgoing;
                        send(outgoing);
                    }
                }
                if (!closed.get()) fail("JAVA_LANGUAGE_EXITED", "Java 提示进程已退出，请重新连接");
            } catch (Exception ex) { if (!closed.get()) fail("JAVA_LANGUAGE_EXITED", "Java 提示进程异常，请重新连接"); }
        });
    }

    private static String xml(String value) { return value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;"); }
    static String runtimeConfigDirectory() {
        String os = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT);
        return os.contains("win") ? "config_win" : os.contains("mac") ? "config_mac" : "config_linux";
    }
    private synchronized void write(JsonNode message) throws IOException { LspFrames.write(process.getOutputStream(), JSON.writeValueAsBytes(message)); }
    private void emit(ObjectNode message, JsonNode result) throws IOException { message.set("result", result); send(JSON.writeValueAsString(message)); }
    private void send(String value) { Consumer<String> sink = output; if (sink != null) sink.accept(value); }
    private void fail(String code, String detail) {
        try { send(JSON.writeValueAsString(JSON.createObjectNode().put("jsonrpc", "2.0").put("method", "datascalpel/status")
                .set("params", JSON.createObjectNode().put("code", code).put("detail", detail)))); }
        catch (Exception ignored) { /* Disconnected client cannot receive a status. */ }
        close();
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        input.shutdownNow();
        // shutdownNow may interrupt this same input worker on a protocol failure.
        // Still wait for the child to exit before removing its locked Eclipse workspace.
        boolean restoreInterrupt = Thread.interrupted();
        Process child;
        synchronized (this) { child = process; }
        if (child != null) {
            child.destroy();
            try { if (!child.waitFor(3, TimeUnit.SECONDS)) { child.destroyForcibly(); child.waitFor(3, TimeUnit.SECONDS); } }
            catch (InterruptedException ex) { child.destroyForcibly(); restoreInterrupt = true; }
        }
        // Only this server-created session directory, never the configured root or a client path.
        if (root.getParent().equals(config.workRoot()) && root.getFileName().toString().startsWith("session-")) {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            } catch (IOException ignored) { /* A locked analysis cache may be cleaned by deployment maintenance. */ }
        }
        if (restoreInterrupt) Thread.currentThread().interrupt();
    }
}
