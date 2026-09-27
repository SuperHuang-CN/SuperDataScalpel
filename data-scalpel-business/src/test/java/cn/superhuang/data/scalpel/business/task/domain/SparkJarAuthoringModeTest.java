package cn.superhuang.data.scalpel.business.task.domain;

import cn.superhuang.data.scalpel.contract.execution.SparkJarJobMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SparkJarAuthoringModeTest {
    @ParameterizedTest
    @EnumSource(SparkJarJobMode.class)
    void choosingModesPreservesRuntimeArtifactDraftAndVersion(SparkJarJobMode mode) {
        var definition = SparkJarTaskDefinition.create(UUID.randomUUID(), mode);
        assertNull(definition.getAuthoringMode());
        assertNull(definition.getOnlineAppliedAt());
        definition.saveOnlineSource("source");
        definition.updateJar("key", "job.jar", "sha", 100, "sample.Job", 1, mode);
        definition.markOnlineSourceCompiled("compiled");
        var appliedAt = definition.getOnlineAppliedAt();
        assertNotNull(appliedAt);
        definition.saveOnlineSource("source");
        int version = definition.getVersion();
        definition.chooseAuthoringMode(SparkJarAuthoringMode.UPLOAD);
        definition.chooseAuthoringMode(null);
        assertEquals(SparkJarAuthoringMode.UPLOAD, definition.getAuthoringMode());
        assertEquals("source", definition.getOnlineSourceCode());
        assertEquals("compiled", definition.getOnlineCompiledSourceSha256());
        assertEquals("key", definition.getJarObjectKey());
        definition.chooseAuthoringMode(SparkJarAuthoringMode.ONLINE);
        assertEquals(SparkJarAuthoringMode.ONLINE, definition.getAuthoringMode());
        assertEquals(version, definition.getVersion());
        assertEquals(appliedAt, definition.getOnlineAppliedAt());
    }

    @ParameterizedTest
    @EnumSource(SparkJarJobMode.class)
    void legacyUploadedJarAndExplicitReplacementRetainSource(SparkJarJobMode mode) {
        var definition = SparkJarTaskDefinition.create(UUID.randomUUID(), mode);
        definition.updateJar("key", "job.jar", "sha", 100, "sample.Job", 1, mode);
        assertEquals(SparkJarAuthoringMode.UPLOAD, definition.getAuthoringMode());
        definition.saveOnlineSource("draft");
        definition.markOnlineSourceCompiled("compiled");
        definition.markJarUploaded();
        assertEquals(SparkJarAuthoringMode.UPLOAD, definition.getAuthoringMode());
        assertNull(definition.getOnlineCompiledSourceSha256());
        assertNull(definition.getOnlineAppliedAt());
        assertEquals("draft", definition.getOnlineSourceCode());
    }
}
