package cn.superhuang.datascalpel.taskengine.language;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class LspFramesTest {
    private InputStream bytes(String value) { return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8)); }
    @Test void framesUnicodeAndConsecutiveMessages() throws Exception {
        var output = new ByteArrayOutputStream();
        byte[] source = "{\"text\":\"中文模型\"}".getBytes(StandardCharsets.UTF_8);
        LspFrames.write(output, source); LspFrames.write(output, source);
        var input = new ByteArrayInputStream(output.toByteArray());
        assertArrayEquals(source, LspFrames.read(input)); assertArrayEquals(source, LspFrames.read(input));
        assertNull(LspFrames.read(input));
    }
    @Test void rejectsInvalidLengthAndTruncationBeforeAllocation() {
        for (String header : new String[]{"Content-Length: -1", "Content-Length: 2147483647", "Content-Length: nope",
                "Content-Length: 1\r\nContent-Length: 1", "Unknown: 2"})
            assertThrows(IOException.class, () -> LspFrames.read(bytes(header + "\r\n\r\nx")));
        assertThrows(EOFException.class, () -> LspFrames.read(bytes("Content-Length: 12\r\n\r\nx")));
        assertThrows(IOException.class, () -> LspFrames.read(bytes("x".repeat(8193))));
    }
    @Test void onlyAllowsManagedJavaPaths() throws Exception {
        JavaLanguageWorkspace.validateUri("file:///datascalpel/src/com/example/Job.java");
        for (String uri : new String[]{"file:///etc/passwd", "file:///datascalpel/src/../Job.java",
                "file:///datascalpel/src/%2e%2e/Job.java", "file:///datascalpel/src/pom.xml", "file:///datascalpel/src/a/Job.java?x"})
            assertThrows(IOException.class, () -> JavaLanguageWorkspace.validateUri(uri));
        assertThrows(IOException.class, () -> JavaLanguageWorkspace.validateSource("x".repeat(256 * 1024 + 1)));
        assertThrows(IOException.class, () -> JavaLanguageWorkspace.validateSource("a\0b"));
    }
    @Test void boundedDeploymentSettingsDoNotCollideWithDispatcherPort() {
        var config = LanguageServiceConfiguration.from(Map.of(), 8091);
        assertEquals(8191, config.port()); assertEquals(4, config.maxSessions()); assertNull(config.home());
        assertThrows(IllegalArgumentException.class, () -> LanguageServiceConfiguration.from(Map.of("DATASCALPEL_LANGUAGE_MAX_SESSIONS", "1000"), 8091));
    }
}
