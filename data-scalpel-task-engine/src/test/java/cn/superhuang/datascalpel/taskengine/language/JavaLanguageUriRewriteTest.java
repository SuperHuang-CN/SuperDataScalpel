package cn.superhuang.datascalpel.taskengine.language;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class JavaLanguageUriRewriteTest {
    @TempDir Path work;
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test void largeCompletionAndWorkspaceEditsPreserveValuesAndRoundTripUris() throws Exception {
        var config = new LanguageServiceConfiguration(null, work.resolve("语言 workspace"), 8191, 1, 512, Duration.ofSeconds(5));
        try (var workspace = new JavaLanguageWorkspace(config)) {
            var projectField = JavaLanguageWorkspace.class.getDeclaredField("project");
            projectField.setAccessible(true);
            Path project = (Path) projectField.get(workspace);
            Files.createDirectories(project);
            var rewrite = JavaLanguageWorkspace.class.getDeclaredMethod("rewrite", JsonNode.class, boolean.class);
            rewrite.setAccessible(true);
            var payload = JSON.createObjectNode();
            var items = payload.putArray("items");
            for (int i = 0; i < 100; i++) {
                var item = items.addObject().put("label", "String(value" + i + ")").put("detail", "java.lang.String")
                        .put("filterText", "String").put("sortText", "000" + i).put("insertText", "String($0)");
                item.putObject("documentation").put("kind", "markdown").put("value", "Constructor documentation");
                item.putObject("textEdit").put("newText", "String($0)").putObject("range")
                        .putObject("start").put("line", 3).put("character", 5);
            }
            String virtualUri = "file:///datascalpel/src/com/example/Job.java";
            payload.putObject("changes").putArray(virtualUri).addObject().put("newText", "class Job {}");
            payload.put("uri", virtualUri);
            payload.put("unrelatedUri", project.resolveSibling("project-other").resolve("Job.java").toUri().toString());
            payload.put("literal", "代码中的普通文字").put("isIncomplete", true).putNull("optional");
            JsonNode incoming = (JsonNode) rewrite.invoke(workspace, payload, true);
            assertEquals(project.resolve("src/com/example/Job.java").toUri().toString(), incoming.path("uri").asText());
            long started = System.nanoTime();
            for (int i = 0; i < 5; i++) assertEquals(payload, rewrite.invoke(workspace, incoming, false));
            System.out.printf("LANGUAGE URI rewrite 100 candidates average ms=%d%n",
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) / 5);
        }
    }
}
