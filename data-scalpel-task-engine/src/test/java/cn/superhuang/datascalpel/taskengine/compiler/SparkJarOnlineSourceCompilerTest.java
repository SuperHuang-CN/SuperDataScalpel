package cn.superhuang.datascalpel.taskengine.compiler;

import cn.superhuang.data.scalpel.contract.task.SparkJarSourceCompilationRequest;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.UUID;
import java.util.jar.JarInputStream;

import static org.junit.jupiter.api.Assertions.*;

class SparkJarOnlineSourceCompilerTest {
    private static final String VALID_SOURCE = """
            package com.example.datascalpel;

            import cn.superhuang.datascalpel.sdk.SparkBatchJob;
            import cn.superhuang.datascalpel.sdk.SparkJobContext;

            public final class ExampleSparkJob implements SparkBatchJob {
                @Override public void execute(SparkJobContext context) {}
            }
            """;

    @Test
    void compilesAContractCheckedDeterministicJarWithoutExecutingTheJob() throws Exception {
        var compiler = new SparkJarOnlineSourceCompiler();
        var first = compiler.compile(new SparkJarSourceCompilationRequest(UUID.randomUUID(), VALID_SOURCE),
                System.nanoTime());
        var second = compiler.compile(new SparkJarSourceCompilationRequest(UUID.randomUUID(), VALID_SOURCE),
                System.nanoTime());

        assertTrue(first.successful());
        assertNotNull(first.jarBytes());
        assertEquals(first.jarSha256(), second.jarSha256());
        assertArrayEquals(first.jarBytes(), second.jarBytes());
        try (JarInputStream jar = new JarInputStream(new ByteArrayInputStream(first.jarBytes()))) {
            assertEquals("1", jar.getManifest().getMainAttributes().getValue("DataScalpel-Job-Api-Version"));
            assertEquals("com.example.datascalpel.ExampleSparkJob",
                    jar.getManifest().getMainAttributes().getValue("DataScalpel-Job-Class"));
        }
    }

    @Test
    void returnsSourceDiagnosticsWhenTheFixedMainClassContractIsNotMet() {
        String source = VALID_SOURCE.replace("public final class ExampleSparkJob", "final class RenamedJob");
        var result = new SparkJarOnlineSourceCompiler().compile(
                new SparkJarSourceCompilationRequest(UUID.randomUUID(), source), System.nanoTime());

        assertFalse(result.successful());
        assertNull(result.jarBytes());
        assertTrue(result.diagnostics().stream().anyMatch(value -> value.severity().name().equals("ERROR")));
    }
}
