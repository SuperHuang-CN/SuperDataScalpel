package cn.superhuang.datascalpel.taskengine.language;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit real-runtime test; never silently substitutes a fake language server. */
@EnabledIfSystemProperty(named = "datascalpel.test.jdtls.home", matches = ".+")
class JavaLanguageWorkspaceIntegrationTest {
    @TempDir Path work;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String URI = "file:///datascalpel/src/com/example/datascalpel/ExampleSparkJob.java";

    @Test void prebuiltIndexesPrepareReuseAndKeepPrivateCopies() throws Exception {
        var config = new LanguageServiceConfiguration(Path.of(System.getProperty("datascalpel.test.jdtls.home")), work, 8191, 2, 512, Duration.ofSeconds(5));
        try (var cache = new JavaLanguageIndexCache(config)) {
            cache.prepare();
            Path probe = java.nio.file.Files.createDirectories(work.resolve("copy-check"));
            assertNotNull(cache.copyToWorkspace(probe), "Background producer must publish a complete matching snapshot");
            var before = JavaLanguageIndexCache.inventory(config.indexCacheRoot());
            for (int i = 0; i < 2; i++) {
                long started = System.nanoTime();
                try (var workspace = new JavaLanguageWorkspace(config, cache::copyToWorkspace)) {
                    var output = new LinkedBlockingQueue<String>();
                    workspace.attach(output::add);
                    initialize(workspace, output);
                    openSource(workspace, "package com.example.datascalpel;\npublic class ExampleSparkJob { void run() { new Strin; } }", 1);
                    assertNotNull(completionAt(workspace, output, 11, "String", 1, 53));
                    open(workspace, "models", 2);
                    assertNotNull(completion(workspace, output, 12, "models"));
                    System.out.printf("PREBUILT_INDEX consumer=%d readyMs=%d%n", i,
                            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
                }
            }
            assertEquals(before, JavaLanguageIndexCache.inventory(config.indexCacheRoot()));
            // A fresh manager must reload a published snapshot without launching another producer.
            try (var reloaded = new JavaLanguageIndexCache(config)) {
                reloaded.prepare();
                assertNotNull(reloaded.copyToWorkspace(java.nio.file.Files.createDirectories(work.resolve("reload-check"))));
                assertEquals(before, JavaLanguageIndexCache.inventory(config.indexCacheRoot()));
            }
            // A broken published file must not be passed to JDT or fail the editor's initialization.
            String index = before.keySet().iterator().next();
            java.nio.file.Files.writeString(config.indexCacheRoot().resolve(index), "corrupt test index");
            assertNull(cache.copyToWorkspace(java.nio.file.Files.createDirectories(work.resolve("corruption-check"))));
        }
    }

    @Test void entryOccurrencesWithSparkWildcardImportAreSemantic() throws Exception {
        var config = new LanguageServiceConfiguration(Path.of(System.getProperty("datascalpel.test.jdtls.home")), work, 8191, 1, 512, Duration.ofSeconds(5));
        try (var workspace = new JavaLanguageWorkspace(config)) {
            var output = new LinkedBlockingQueue<String>();
            workspace.attach(output::add);
            initialize(workspace, output);
            open(workspace, "models", 1);
            assertNotNull(completion(workspace, output, 10, "models"));
            String source = """
                    package com.example.datascalpel;
                    import static org.apache.spark.sql.functions.*;
                    import cn.superhuang.datascalpel.sdk.*;
                    public final class ExampleSparkJob implements SparkBatchJob {
                        public ExampleSparkJob() {}
                        public ExampleSparkJob(int count) {}
                        ExampleSparkJob copy() { return new ExampleSparkJob(); }
                        java.util.function.Supplier<ExampleSparkJob> factory = ExampleSparkJob::new;
                        Class<?> type = ExampleSparkJob.class;
                        String text = "ExampleSparkJob"; // ExampleSparkJob must stay unchanged
                        int ExampleSparkJob = 1;
                        public void execute(SparkJobContext context) { System.out.println(ExampleSparkJob); }
                    }
                    """;
            for (String eol : new String[]{"\n", "\r\n"}) {
                String current = source.replace("\n", eol);
                openSource(workspace, current, eol.length() + 1);
                var params = JSON.createObjectNode(); params.putObject("textDocument").put("uri", URI);
                params.putObject("position").put("line", 3).put("character", 19);
                JsonNode highlights = call(workspace, output, 30 + eol.length(), "textDocument/documentHighlight", params).path("result");
                assertEquals(6, highlights.size(), highlights::toString);
                for (JsonNode highlight : highlights) {
                    JsonNode range = highlight.path("range");
                    int line = range.path("start").path("line").asInt();
                    assertTrue(line >= 3 && line <= 8, highlight::toString);
                    assertEquals("ExampleSparkJob", current.split("\n")[line].substring(
                            range.path("start").path("character").asInt(), range.path("end").path("character").asInt()));
                }
                JsonNode symbols = call(workspace, output, 40 + eol.length(), "textDocument/documentSymbol", params).path("result");
                JsonNode entry = null;
                for (JsonNode symbol : symbols) if (symbol.path("kind").asInt() == 5 && symbol.path("name").asText().equals("ExampleSparkJob")) entry = symbol;
                assertNotNull(entry, symbols::toString);
                assertEquals(3, entry.path("selectionRange").path("start").path("line").asInt());
                int constructors = 0;
                for (JsonNode symbol : entry.path("children")) {
                    if (symbol.path("kind").asInt() != 9) continue;
                    constructors++;
                    JsonNode range = symbol.path("selectionRange");
                    int line = range.path("start").path("line").asInt();
                    assertTrue(line == 4 || line == 5, symbol::toString);
                    assertEquals("ExampleSparkJob", current.split("\n")[line].substring(
                            range.path("start").path("character").asInt(), range.path("end").path("character").asInt()));
                }
                assertEquals(2, constructors, symbols::toString);
            }
        }
    }

    @Test void renameAfterUndoDoesNotLeavePhantomCompilationUnits() throws Exception {
        var config = new LanguageServiceConfiguration(Path.of(System.getProperty("datascalpel.test.jdtls.home")), work, 8191, 1, 512, Duration.ofSeconds(5));
        try (var workspace = new JavaLanguageWorkspace(config)) {
            var output = new LinkedBlockingQueue<String>();
            workspace.attach(output::add);
            initialize(workspace, output);
            open(workspace, "models", 1);
            assertNotNull(completion(workspace, output, 10, "models"));
            String source = "package com.example.datascalpel;\npublic class %s { public %s() {} }";
            int version = 2;
            // Open/rename twice, undo both names, then reuse the previous target name.
            for (String name : new String[]{"ExampleSparkJob", "FirstJob", "SecondJob", "FirstJob", "ExampleSparkJob"}) {
                String uri = URI.replace("ExampleSparkJob.java", name + ".java");
                openSource(workspace, source.formatted(name, name), version++, uri);
                String target = name.equals("ExampleSparkJob") ? "FirstJob" : "SecondJob";
                if (name.equals("SecondJob") || version == 6) continue;
                var params = JSON.createObjectNode(); params.putObject("textDocument").put("uri", uri);
                params.putObject("position").put("line", 1).put("character", 13);
                params.put("newName", target);
                JsonNode result = call(workspace, output, 20 + version, "textDocument/rename", params);
                assertFalse(result.has("error"), result::toString);
                assertTrue(result.path("result").toString().contains(target), result::toString);
            }
        }
    }

    @Test void constructorCompletionLatency() throws Exception {
        var config = new LanguageServiceConfiguration(Path.of(System.getProperty("datascalpel.test.jdtls.home")), work, 8191, 1, 512, Duration.ofSeconds(5));
        try (var workspace = new JavaLanguageWorkspace(config)) {
            var output = new LinkedBlockingQueue<String>();
            workspace.attach(output::add);
            long start = System.nanoTime();
            initialize(workspace, output);
            System.out.printf("LANGUAGE initialize ms=%d%n", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
            openSource(workspace, "package com.example.datascalpel;\npublic class ExampleSparkJob {\n void work() {\n  new Strin\n }\n}", 1);
            boolean found = false;
            JsonNode stringConstructor = null;
            for (int attempt = 0; attempt < 8; attempt++) {
                var params = JSON.createObjectNode(); params.putObject("textDocument").put("uri", URI);
                params.putObject("position").put("line", 3).put("character", 11);
                long before = System.nanoTime();
                JsonNode result = call(workspace, output, 100 + attempt, "textDocument/completion", params).path("result");
                JsonNode items = result.isArray() ? result : result.path("items");
                boolean hasString = false;
                for (JsonNode item : items) if (item.path("label").asText().equals("String()")) {
                    hasString = true;
                    stringConstructor = item;
                }
                if (found) assertTrue(hasString, "Warm completion must retain String() every time");
                found |= hasString;
                System.out.printf("LANGUAGE constructor attempt=%d ms=%d count=%d string=%s incomplete=%s%n", attempt,
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - before), items.size(), hasString, result.path("isIncomplete"));
            }
            assertTrue(found, "JDK String constructors must be discoverable");
            JsonNode resolved = call(workspace, output, 120, "completionItem/resolve", stringConstructor).path("result");
            assertTrue(resolved.path("textEdit").path("newText").asText().contains("String("), resolved::toString);
            assertTrue(resolved.path("textEdit").path("newText").asText().contains(")"), "Fast completion must still insert a constructor call");
            openSource(workspace, "package com.example.datascalpel;\npublic class ExampleSparkJob {\n void work() {\n  new Str\n }\n}", 2);
            assertNotNull(completionAt(workspace, output, 121, "String", 3, 9), "Short type prefix must find String");
            openSource(workspace, "package com.example.datascalpel;\npublic class ExampleSparkJob {\n void work() {\n  new String().trim();\n }\n}", 3);
            long before = System.nanoTime();
            assertNotNull(completionAt(workspace, output, 122, "trim", 3, 15));
            System.out.printf("LANGUAGE chained String methods ms=%d%n", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - before));
        }
    }

    @Test void independentProcessesSupportTypesResolveDiagnosticsAndReconnect() throws Exception {
        var config = new LanguageServiceConfiguration(Path.of(System.getProperty("datascalpel.test.jdtls.home")), work, 8191, 2, 512, Duration.ofSeconds(5));
        try (var a = new JavaLanguageWorkspace(config); var b = new JavaLanguageWorkspace(config)) {
            var outA = new LinkedBlockingQueue<String>(); var outB = new LinkedBlockingQueue<String>();
            assertTrue(a.attach(outA::add)); assertTrue(b.attach(outB::add)); assertFalse(a.attach(outA::add));
            initialize(a, outA); initialize(b, outB);
            open(a, "models", 1); open(b, "jdbc", 1);
            JsonNode candidateA = completion(a, outA, 10, "models");
            JsonNode candidateB = completion(b, outB, 20, "jdbc");
            assertNotNull(candidateA); assertNotNull(candidateB);
            // B's completion must not invalidate A's completion-item cache.
            a.accept(JSON.writeValueAsString(JSON.createObjectNode().put("jsonrpc", "2.0").put("id", 11)
                    .put("method", "completionItem/resolve").set("params", candidateA)));
            assertFalse(response(outA, 11).has("error"));
            a.detach(); assertTrue(a.attach(outA::add));
            a.accept("{\"jsonrpc\":\"2.0\",\"id\":12,\"method\":\"initialize\",\"params\":{}}");
            assertTrue(response(outA, 12).path("result").has("capabilities"));
            open(a, "models", 2);
            assertNotNull(completion(a, outA, 13, "models"));
            var hover = JSON.createObjectNode(); hover.putObject("textDocument").put("uri", URI);
            hover.putObject("position").put("line", 3).put("character", 22);
            assertFalse(call(a, outA, 30, "textDocument/hover", hover).path("result").isNull());
            var rename = JSON.createObjectNode(); rename.putObject("textDocument").put("uri", URI);
            rename.putObject("position").put("line", 2).put("character", 20);
            rename.put("newName", "MeterJob");
            JsonNode renamed = call(a, outA, 31, "textDocument/rename", rename);
            assertFalse(renamed.has("error"), renamed::toString);
            assertTrue(renamed.path("result").toString().contains("MeterJob"), renamed::toString);
            assertTrue(renamed.path("result").toString().contains(URI), "Rename must use browser document URIs");
            var format = JSON.createObjectNode(); format.putObject("textDocument").put("uri", URI);
            format.putObject("options").put("tabSize", 4).put("insertSpaces", true);
            assertTrue(call(a, outA, 32, "textDocument/formatting", format).path("result").isArray());
            // JDT's optional diagnostic version is absent in 1.60; validate the actual contract.
            open(a, "missingMethod", 3);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            JsonNode diagnostic = null;
            var observedDiagnostics = new java.util.ArrayList<JsonNode>();
            while (System.nanoTime() < deadline) {
                String raw = outA.poll(1, TimeUnit.SECONDS); if (raw == null) continue;
                JsonNode event = JSON.readTree(raw);
                if (event.path("method").asText().equals("textDocument/publishDiagnostics")) observedDiagnostics.add(event);
                if (event.path("method").asText().equals("textDocument/publishDiagnostics")
                        && event.path("params").path("uri").asText().equals(URI)
                        && event.path("params").path("diagnostics").toString().contains("missingMethod")
                        && !event.path("params").path("diagnostics").isEmpty()) {
                    diagnostic = event; break;
                }
            }
            assertNotNull(diagnostic, "Real diagnostics must identify the invalid SDK method: " + observedDiagnostics);
            String genericSource = "package com.example.datascalpel;\nimport java.util.List;\npublic class ExampleSparkJob {\n void work() {\n  List<String> names = List.of(\"a\");\n  names.get(0).trim();\n }\n}";
            openSource(a, genericSource, 4);
            assertNotNull(completionAt(a, outA, 40, "trim", 5, 15), "Generic List<String> method chain must resolve String methods");
            var signature = JSON.createObjectNode(); signature.putObject("textDocument").put("uri", URI);
            signature.putObject("position").put("line", 5).put("character", 12);
            assertFalse(call(a, outA, 41, "textDocument/signatureHelp", signature).path("result").path("signatures").isEmpty(), "List.get parameter help must be available");
            openSource(a, "package com.example.datascalpel;\npublic class ExampleSparkJob {\n ArrayList<String> names;\n}", 5);
            var imports = JSON.createObjectNode(); imports.putObject("textDocument").put("uri", URI);
            imports.putObject("position").put("line", 2).put("character", 10);
            JsonNode items = call(a, outA, 42, "textDocument/completion", imports).path("result");
            JsonNode arrayList = null;
            for (JsonNode item : items.isArray() ? items : items.path("items"))
                if (item.path("label").asText().startsWith("ArrayList")) { arrayList = item; break; }
            assertNotNull(arrayList, "Unimported java.util.ArrayList must be suggested");
            JsonNode resolved = call(a, outA, 43, "completionItem/resolve", arrayList).path("result");
            assertTrue(resolved.path("additionalTextEdits").toString().contains("import java.util.ArrayList"), resolved::toString);
        }
        try (var entries = java.nio.file.Files.list(work)) { assertEquals(0, entries.count(), "Session directories must be reclaimed"); }
    }

    @Test void protocolFailureReclaimsRunningChildWorkspace() throws Exception {
        var config = new LanguageServiceConfiguration(Path.of(System.getProperty("datascalpel.test.jdtls.home")), work, 8191, 1, 512, Duration.ofSeconds(5));
        try (var workspace = new JavaLanguageWorkspace(config)) {
            var output = new LinkedBlockingQueue<String>();
            assertTrue(workspace.attach(output::add));
            initialize(workspace, output);
            workspace.accept("{\"jsonrpc\":\"2.0\",\"method\":\"unsupported\"}");
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            boolean reclaimed = false;
            while (System.nanoTime() < deadline) {
                try (var entries = java.nio.file.Files.list(work)) { reclaimed = entries.findAny().isEmpty(); }
                if (reclaimed) break;
                output.poll(100, TimeUnit.MILLISECONDS);
            }
            assertTrue(reclaimed, "Protocol failure from the input worker must terminate the child and clean its workspace");
        }
    }

    private JsonNode call(JavaLanguageWorkspace ws, LinkedBlockingQueue<String> output, int id, String method, JsonNode params) throws Exception {
        ws.accept(JSON.writeValueAsString(JSON.createObjectNode().put("jsonrpc", "2.0").put("id", id).put("method", method).set("params", params)));
        return response(output, id);
    }

    private void initialize(JavaLanguageWorkspace ws, LinkedBlockingQueue<String> output) throws Exception {
        ws.accept("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}");
        assertTrue(response(output, 1).path("result").has("capabilities"));
        ws.accept("{\"jsonrpc\":\"2.0\",\"method\":\"initialized\",\"params\":{}}");
    }
    private void open(JavaLanguageWorkspace ws, String method, int version) throws Exception {
        String source = "package com.example.datascalpel;\nimport cn.superhuang.datascalpel.sdk.*;\npublic class ExampleSparkJob implements SparkBatchJob {\n public void execute(SparkJobContext context) {\n  context." + method + "();\n }\n}";
        openSource(ws, source, version);
    }
    private void openSource(JavaLanguageWorkspace ws, String source, int version) throws Exception {
        openSource(ws, source, version, URI);
    }
    private void openSource(JavaLanguageWorkspace ws, String source, int version, String uri) throws Exception {
        var params = JSON.createObjectNode(); params.putObject("textDocument").put("uri", uri).put("languageId", "java").put("version", version).put("text", source);
        ws.accept(JSON.writeValueAsString(JSON.createObjectNode().put("jsonrpc", "2.0").put("method", "textDocument/didOpen").set("params", params)));
    }
    private JsonNode completion(JavaLanguageWorkspace ws, LinkedBlockingQueue<String> output, int id, String expected) throws Exception {
        return completionAt(ws, output, id, expected, 4, 10);
    }
    private JsonNode completionAt(JavaLanguageWorkspace ws, LinkedBlockingQueue<String> output, int id, String expected, int line, int character) throws Exception {
        // Initial import is asynchronous. Retry a bounded number of actual requests, not a fixed sleep.
        for (int attempt = 0; attempt < 10; attempt++) {
            var params = JSON.createObjectNode(); params.putObject("textDocument").put("uri", URI);
            params.putObject("position").put("line", line).put("character", character);
            ws.accept(JSON.writeValueAsString(JSON.createObjectNode().put("jsonrpc", "2.0").put("id", id).put("method", "textDocument/completion").set("params", params)));
            JsonNode response = response(output, id); assertFalse(response.has("error"), response::toString);
            JsonNode result = response.path("result");
            for (JsonNode item : result.isArray() ? result : result.path("items")) if (item.path("label").asText().startsWith(expected + "(")) return item;
        }
        fail("No semantic SDK completion for " + expected); return null;
    }
    private JsonNode response(LinkedBlockingQueue<String> output, int id) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120);
        while (System.nanoTime() < deadline) {
            String raw = output.poll(1, TimeUnit.SECONDS); if (raw == null) continue;
            JsonNode message = JSON.readTree(raw);
            if (message.path("method").asText().equals("datascalpel/status")) fail(message.toString());
            if (message.path("id").asInt(-1) == id) return message;
        }
        fail("Timed out waiting for real JDT LS response " + id); return null;
    }
}
