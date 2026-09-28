package cn.superhuang.datascalpel.taskengine.build;

import cn.superhuang.data.scalpel.contract.task.SdkApiDocumentation;
import cn.superhuang.datascalpel.sdk.SparkJobContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class SdkApiDocumentationTest {
    @TempDir Path temp;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void coversPublicMethodsAndCompilesEveryExample() throws Exception {
        SdkApiDocumentation doc;
        try (var input = SparkJobContext.class.getResourceAsStream("/META-INF/datascalpel/sdk-api.json")) {
            assertNotNull(input);
            doc = json.readValue(input, SdkApiDocumentation.class);
        }
        assertFalse(doc.types().isEmpty());
        assertTrue(doc.types().getFirst().summary().contains("入口"));
        var contextDoc = doc.types().stream().filter(type -> type.simpleName().equals("SparkJobContext")).findFirst().orElseThrow();
        assertTrue(contextDoc.note().contains("execute(SparkJobContext context)"));
        assertTrue(contextDoc.members().stream().allMatch(member -> !member.example().isBlank()), "Context entry points need actual receiver-qualified calls");
        var spark = contextDoc.members().stream().filter(member -> member.name().equals("spark")).findFirst().orElseThrow();
        assertTrue(spark.example().contains("var spark = context.spark();"));
        assertTrue(spark.example().contains("spark.range(5).show();"));
        assertTrue(spark.note().contains("不是预置变量"));
        for (String resourceType : List.of("ModelResources", "JdbcResources", "SparkJobParameters", "SparkStreamingJobContext")) {
            var resourceDoc = doc.types().stream().filter(type -> type.simpleName().equals(resourceType)).findFirst().orElseThrow();
            assertTrue(resourceDoc.members().stream().allMatch(member -> !member.example().isBlank()), resourceType + " needs an example for each overload");
        }
        var examples = new ArrayList<Path>();
        for (var type : doc.types()) {
            assertFalse(type.summary().contains("\\u"), "Chinese must remain readable");
            String binaryName = type.name();
            if (binaryName.contains("JdbcReadOptions.")) binaryName = binaryName.replace("JdbcReadOptions.", "JdbcReadOptions$");
            Class<?> api = Class.forName(binaryName);
            assertEquals(api.getName().substring(api.getPackageName().length() + 1).replace('$', '.'), type.simpleName());
            var unique = new HashSet<String>();
            for (var member : type.members()) {
                assertTrue(unique.add(member.signature()), "Duplicate member " + type.name() + "." + member.signature());
                assertFalse(member.summary().isBlank());
                assertTrue(member.parameters().stream().allMatch(p -> !p.description().isBlank()));
            }
            for (var method : api.getDeclaredMethods()) {
                if (!Modifier.isPublic(method.getModifiers()) || method.isSynthetic()
                        || Set.of("equals", "hashCode", "toString", "values", "valueOf").contains(method.getName())) continue;
                assertTrue(type.members().stream().anyMatch(m -> m.name().equals(method.getName()) && m.parameters().size() == method.getParameterCount()), method.toString());
            }
            var snippets = new ArrayList<String>();
            if (!type.example().isBlank()) snippets.add(type.example());
            type.members().stream().map(SdkApiDocumentation.ApiMember::example).filter(e -> !e.isBlank()).forEach(snippets::add);
            for (String snippet : snippets) {
                String name = "Example" + examples.size();
                String context = type.mode().equals("STREAMING") ? "SparkStreamingJobContext" : "SparkJobContext";
                String body = snippet.startsWith("public void ") ? snippet : "void run(" + context + " context) throws Exception {\n" + snippet + "\n}";
                Path source = temp.resolve(name + ".java");
                Files.writeString(source, "import cn.superhuang.datascalpel.sdk.*;\nclass " + name + " {\n" + body + "\n}");
                examples.add(source);
            }
        }
        assertTrue(examples.size() >= 8);
        var args = new ArrayList<>(List.of("-proc:none", "-encoding", "UTF-8", "-classpath", System.getProperty("java.class.path"), "-d", temp.toString()));
        examples.forEach(p -> args.add(p.toString()));
        var errors = new ByteArrayOutputStream();
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, errors, errors, args.toArray(String[]::new)), errors.toString(StandardCharsets.UTF_8));
    }

    @Test
    void sourceChangesAutomaticallyUpdateCatalogueAndIncompleteDocsFailBuild() throws Exception {
        Path source = Files.createDirectories(temp.resolve("src")).resolve("FreshApi.java");
        Path target = temp.resolve("docs.json");
        Files.writeString(source, fixture("first", true));
        assertEquals(0, generate(source.getParent(), target));
        var first = json.readTree(target.toFile());
        assertEquals("新的公开方法。", first.at("/types/0/members/0/summary").asText());
        assertEquals("参数说明。", first.at("/types/0/members/0/parameters/0/description").asText());
        Files.writeString(source, fixture("replacement", true));
        assertEquals(0, generate(source.getParent(), target));
        var next = json.readTree(target.toFile());
        assertEquals("replacement", next.at("/types/0/members/0/name").asText());
        assertNotEquals(first.get("fingerprint"), next.get("fingerprint"));
        Files.writeString(source, fixture("undocumented", false));
        assertNotEquals(0, generate(source.getParent(), target));
        assertFalse(Files.exists(target), "Never publish stale documentation after failed generation");
    }

    private String fixture(String method, boolean documented) {
        return "/** 动态中文类型。\n * @apiGroup 新能力\n */\npublic interface FreshApi {\n"
                + (documented ? "/** 新的公开方法。\n * @param value 参数说明。\n */\n" : "")
                + "void " + method + "(String value);\n}";
    }

    private int generate(Path source, Path output) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        if (!Files.isDirectory(root.resolve("data-scalpel-task-sdk"))) root = root.getParent();
        Path generator = root.resolve("data-scalpel-task-sdk/src/build/java/SdkApiDocGenerator.java");
        var log = temp.resolve(UUID.randomUUID() + ".log");
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), generator.toString(),
                source.toString(), output.toString(), "test", ".").redirectErrorStream(true).redirectOutput(log.toFile()).start();
        if (!process.waitFor(Duration.ofSeconds(45).toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            fail("Doc generator timed out: " + Files.readString(log));
        }
        return process.exitValue();
    }
}
