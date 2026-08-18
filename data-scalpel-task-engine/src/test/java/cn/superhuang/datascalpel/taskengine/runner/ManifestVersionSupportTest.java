package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.contract.task.MetadataSnapshot;
import cn.superhuang.data.scalpel.contract.task.TaskDefinition;
import cn.superhuang.datascalpel.taskengine.contract.TaskExecutionManifest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ManifestVersionSupportTest {

    @Test
    void rejectsPreviousManifestVersion() {
        TaskExecutionManifest manifest = manifest(
                TaskExecutionManifest.PREVIOUS_MANIFEST_VERSION,
                null,
                new MetadataSnapshot(List.of(), List.of())
        );

        RunnerExecutionException exception = assertThrows(
                RunnerExecutionException.class,
                () -> ManifestVersionSupport.requireSupported(manifest)
        );

        assertEquals("INVALID_MANIFEST", exception.code());
    }

    @Test
    void acceptsOnlyCurrentManifestVersion() {
        TaskExecutionManifest manifest = manifest(
                TaskExecutionManifest.CURRENT_MANIFEST_VERSION,
                null,
                new MetadataSnapshot(List.of(), List.of())
        );

        assertDoesNotThrow(() -> ManifestVersionSupport.requireSupported(manifest));
    }

    private static TaskExecutionManifest manifest(
            int version,
            TaskDefinition task,
            MetadataSnapshot metadataSnapshot
    ) {
        return new TaskExecutionManifest(version, null, task, metadataSnapshot, List.of());
    }
}
