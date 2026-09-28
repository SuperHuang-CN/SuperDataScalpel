package cn.superhuang.datascalpel.taskengine.language;

import cn.superhuang.datascalpel.taskengine.compiler.SparkJarOnlineSourceCompiler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;

/** Published binary indexes are never passed to JDT: every consumer gets an ordinary private copy.
 * JDT LS may rewrite its sharedIndexLocation, even when it starts from prebuilt indexes. */
final class JavaLanguageIndexCache implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(JavaLanguageIndexCache.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private final LanguageServiceConfiguration config;
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("java-language-index-cache").factory());
    private volatile Snapshot ready;
    private volatile boolean closed;

    JavaLanguageIndexCache(LanguageServiceConfiguration config) { this.config = config; }

    void start() {
        if (!config.indexCacheEnabled() || config.home() == null || !Files.isDirectory(config.home().resolve("plugins"))) return;
        // Preparation never blocks a browser's initialize request. Retry failures at a bounded rate.
        worker.scheduleWithFixedDelay(this::prepare, 0, 5, TimeUnit.MINUTES);
    }

    Path copyToWorkspace(Path workspace) {
        Snapshot snapshot = ready;
        if (snapshot == null || closed) return null;
        long started = System.nanoTime();
        try {
            // Hash the actual inputs, not only their versions or mtimes (SNAPSHOT jars can be replaced).
            if (!snapshot.manifest().fingerprint().equals(fingerprint(config))) {
                ready = null;
                LOG.info("Java language index inputs changed; using independent indexing until preparation completes");
                return null;
            }
            Path copy = copyVerified(snapshot.directory(), snapshot.manifest(), workspace.resolve("indexes"));
            LOG.info("Java language prebuilt indexes copied and verified in {} ms", elapsed(started));
            return copy;
        } catch (Exception ex) {
            ready = null;
            LOG.warn("Java language index cache unavailable ({}); using independent indexing", ex.getClass().getSimpleName());
            return null;
        }
    }

    void prepare() {
        if (closed || !config.indexCacheEnabled() || config.home() == null) return;
        Path staging = null;
        try {
            String fingerprint = fingerprint(config);
            Snapshot current = ready;
            if (current != null && current.manifest().fingerprint().equals(fingerprint)) return;
            ready = null;
            Files.createDirectories(config.indexCacheRoot());
            try (var channel = FileChannel.open(config.indexCacheRoot().resolve("prepare.lock"),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE); var lock = channel.tryLock()) {
                if (lock == null || closed) return;
                Snapshot existing = findPublished(fingerprint);
                if (existing != null) {
                    ready = existing;
                    LOG.info("Java language prebuilt index snapshot loaded ({} files)", existing.manifest().files().size());
                    return;
                }
                staging = Files.createTempDirectory(config.indexCacheRoot(), "building-");
                long started = System.nanoTime();
                LOG.info("Preparing Java language fixed-dependency indexes in the background");
                generate(staging);
                if (closed || !fingerprint.equals(fingerprint(config))) throw new IOException("Index inputs changed while preparing");
                Map<String, IndexFile> files = inventory(staging);
                verifyExpected(files, expectedIndexes(config));
                Manifest manifest = new Manifest(fingerprint, files);
                Files.write(staging.resolve("manifest.json"), JSON.writeValueAsBytes(manifest));
                Path published = config.indexCacheRoot().resolve("snapshot-" + fingerprint + "-" + UUID.randomUUID());
                // Same filesystem; only a complete, stopped producer's directory becomes visible.
                Files.move(staging, published, StandardCopyOption.ATOMIC_MOVE);
                staging = null;
                ready = new Snapshot(published, manifest);
                LOG.info("Java language index snapshot ready: {} files, {} ms", files.size(), elapsed(started));
            }
        } catch (Exception ex) {
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
            if (!closed) LOG.warn("Java language index preparation failed ({}); normal editing remains available", ex.getClass().getSimpleName());
        } finally {
            if (staging != null) removeStaging(staging);
        }
    }

    private Snapshot findPublished(String fingerprint) throws IOException {
        try (var dirs = Files.list(config.indexCacheRoot())) {
            for (Path dir : dirs.filter(p -> p.getFileName().toString().startsWith("snapshot-" + fingerprint + "-")).sorted().toList()) {
                try {
                    Path file = dir.resolve("manifest.json");
                    if (!Files.isDirectory(dir) || Files.size(file) > 2 * 1024 * 1024) continue;
                    Manifest manifest = JSON.readValue(Files.readAllBytes(file), Manifest.class);
                    if (fingerprint.equals(manifest.fingerprint()) && manifest.files().equals(inventory(dir))) {
                        verifyExpected(manifest.files(), expectedIndexes(config));
                        return new Snapshot(dir, manifest);
                    }
                } catch (IOException | RuntimeException ignored) { /* Incomplete/corrupt cache is never used. */ }
            }
        }
        return null;
    }

    private void generate(Path staging) throws Exception {
        var messages = new LinkedBlockingQueue<String>(256);
        Map<String, Long> expected = expectedIndexes(config);
        long deadline = System.nanoTime() + Duration.ofSeconds(120).toNanos();
        try (var workspace = new JavaLanguageWorkspace(config, root -> staging)) {
            workspace.attach(messages::offer);
            request(workspace, messages, 1, "initialize", JSON.createObjectNode(), deadline);
            workspace.accept("{\"jsonrpc\":\"2.0\",\"method\":\"initialized\",\"params\":{}}");
            String uri = "file:///datascalpel/src/IndexProbe.java";
            var open = JSON.createObjectNode();
            open.putObject("textDocument").put("uri", uri).put("languageId", "java").put("version", 1)
                    .put("text", "public class IndexProbe { void probe() { new Strin; } }");
            workspace.accept(JSON.writeValueAsString(JSON.createObjectNode().put("jsonrpc", "2.0")
                    .put("method", "textDocument/didOpen").set("params", open)));
            var params = JSON.createObjectNode();
            params.putObject("textDocument").put("uri", uri);
            params.putObject("position").put("line", 0).put("character", 50);
            params.putObject("context").put("triggerKind", 1);
            boolean found = false;
            for (int i = 0; i < 12 && !found; i++) {
                JsonNode result = request(workspace, messages, i + 2, "textDocument/completion", params, deadline);
                for (JsonNode item : result.isArray() ? result : result.path("items"))
                    if (item.path("label").asText().startsWith("String(")) found = true;
                if (!found && !result.path("isIncomplete").asBoolean()) throw new IOException("JDK index probe failed");
            }
            if (!found) throw new IOException("JDK index probe incomplete");
            // JDT LS exports only completed external-library indexes. Require all expected filenames,
            // library mtimes, and stable content, not ServiceReady or a fixed startup sleep.
            Map<String, IndexFile> previous = Map.of();
            while (!closed && System.nanoTime() < deadline) {
                Map<String, IndexFile> files = inventory(staging);
                if (files.equals(previous) && complete(files, expected)) return;
                previous = files;
                Thread.sleep(500);
            }
            throw new IOException("Index export timed out");
        }
    }

    private static JsonNode request(JavaLanguageWorkspace workspace, LinkedBlockingQueue<String> messages,
                                    int id, String method, JsonNode params, long deadline) throws Exception {
        workspace.accept(JSON.writeValueAsString(JSON.createObjectNode().put("jsonrpc", "2.0")
                .put("id", id).put("method", method).set("params", params)));
        while (System.nanoTime() < deadline) {
            String raw = messages.poll(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            if (raw == null) break;
            JsonNode value = JSON.readTree(raw);
            if (value.path("method").asText().equals("datascalpel/status")) throw new IOException("Index preparation process failed");
            if (value.has("id") && value.path("id").asInt(-1) == id) {
                if (value.has("error")) throw new IOException("Index preparation request failed");
                return value.path("result");
            }
        }
        throw new IOException("Index preparation request timed out");
    }

    static String fingerprint(LanguageServiceConfiguration config) throws IOException {
        List<Path> inputs = new ArrayList<>(SparkJarOnlineSourceCompiler.compilerClasspath());
        Path jdk = Path.of(System.getProperty("java.home"));
        inputs.add(jdk.resolve("lib/modules"));
        inputs.add(jdk.resolve("lib/jrt-fs.jar"));
        inputs.add(jdk.resolve("release"));
        inputs.add(config.home().resolve("plugins"));
        inputs.add(config.home().resolve(JavaLanguageWorkspace.runtimeConfigDirectory()));
        return fingerprintInputs(inputs);
    }

    static String fingerprintInputs(List<Path> inputs) throws IOException {
        MessageDigest digest = sha256();
        digest.update("datascalpel-index-snapshot-v1".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        for (Path input : inputs) {
            Path absolute = input.toAbsolutePath().normalize();
            digest.update(JSON.writeValueAsBytes(absolute.toString()));
            if (Files.isDirectory(absolute)) {
                try (var paths = Files.walk(absolute)) {
                    for (Path file : paths.filter(Files::isRegularFile).sorted().toList()) fingerprintFile(digest, file);
                }
            } else fingerprintFile(digest, absolute);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void fingerprintFile(MessageDigest digest, Path file) throws IOException {
        long modified = Files.getLastModifiedTime(file).toMillis(), size = Files.size(file);
        digest.update(JSON.writeValueAsBytes(List.of(file.toString(), modified, size, hash(file))));
        if (modified != Files.getLastModifiedTime(file).toMillis() || size != Files.size(file))
            throw new IOException("Index dependency changed while hashing");
    }

    private static Map<String, Long> expectedIndexes(LanguageServiceConfiguration config) throws IOException {
        List<Path> libraries = new ArrayList<>(SparkJarOnlineSourceCompiler.compilerClasspath());
        libraries.add(Path.of(System.getProperty("java.home"), "lib", "jrt-fs.jar"));
        Map<String, Long> expected = new TreeMap<>();
        for (Path library : libraries) {
            if (!Files.isRegularFile(library)) continue; // Exploded SDK classes retain a local project index.
            CRC32 crc = new CRC32();
            crc.update(library.toAbsolutePath().toString().replace('\\', '/').getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if (expected.put(crc.getValue() + ".index", Files.getLastModifiedTime(library).toMillis()) != null)
                throw new IOException("Shared index name collision");
        }
        return expected;
    }

    private static boolean complete(Map<String, IndexFile> files, Map<String, Long> expected) {
        try { verifyExpected(files, expected); return true; } catch (IOException ex) { return false; }
    }

    private static void verifyExpected(Map<String, IndexFile> files, Map<String, Long> expected) throws IOException {
        if (files.isEmpty() || files.size() != expected.size()) throw new IOException("Incomplete index snapshot");
        String version = null;
        for (var entry : files.entrySet()) {
            String[] parts = entry.getKey().split("/");
            if (parts.length != 2 || !parts[0].matches("[0-9]+\\.[0-9]+") || !parts[1].matches("[0-9]+\\.index")
                    || !expected.containsKey(parts[1]) || entry.getValue().size() <= 0
                    || entry.getValue().modified() != expected.get(parts[1])) throw new IOException("Unexpected index snapshot");
            if (version != null && !version.equals(parts[0])) throw new IOException("Mixed index formats");
            version = parts[0];
        }
    }

    static Map<String, IndexFile> inventory(Path directory) throws IOException {
        Map<String, IndexFile> files = new TreeMap<>();
        try (var paths = Files.walk(directory)) {
            for (Path file : paths.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".index")).sorted().toList()) {
                files.put(directory.relativize(file).toString().replace('\\', '/'),
                        new IndexFile(Files.size(file), Files.getLastModifiedTime(file).toMillis(), hash(file)));
            }
        }
        return files;
    }

    static Path copyVerified(Path published, Manifest manifest, Path destination) throws IOException {
        for (var entry : manifest.files().entrySet()) {
            if (!entry.getKey().matches("[0-9]+\\.[0-9]+/[0-9]+\\.index")) throw new IOException("Invalid index path");
            Path to = destination.resolve(entry.getKey());
            Files.createDirectories(to.getParent());
            // No hard links: a JDT replacement/write must never modify the published seed or another session.
            Files.copy(published.resolve(entry.getKey()), to, StandardCopyOption.COPY_ATTRIBUTES);
        }
        if (manifest.files().isEmpty() || !manifest.files().equals(inventory(destination))) throw new IOException("Index checksum mismatch");
        return destination;
    }

    private static String hash(Path file) throws IOException {
        MessageDigest digest = sha256();
        try (var input = Files.newInputStream(file)) {
            byte[] buffer = new byte[128 * 1024];
            int count;
            while ((count = input.read(buffer)) >= 0) digest.update(buffer, 0, count);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    private static long elapsed(long start) { return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start); }

    private void removeStaging(Path directory) {
        if (!directory.toAbsolutePath().normalize().getParent().equals(config.indexCacheRoot().toAbsolutePath().normalize())
                || !directory.getFileName().toString().startsWith("building-")) return;
        try (var paths = Files.walk(directory)) {
            for (Path file : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
        } catch (IOException ex) { LOG.debug("Index staging cleanup deferred: {}", ex.getClass().getSimpleName()); }
    }

    @Override public void close() {
        closed = true;
        ready = null;
        worker.shutdownNow();
        try { worker.awaitTermination(10, TimeUnit.SECONDS); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
    }

    record IndexFile(long size, long modified, String sha256) {}
    record Manifest(String fingerprint, Map<String, IndexFile> files) {}
    private record Snapshot(Path directory, Manifest manifest) {}
}
