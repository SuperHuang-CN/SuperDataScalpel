package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.contract.task.JdbcOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcWriteMode;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.datascalpel.taskengine.contract.TaskExecutionManifest;

final class ManifestVersionSupport {
    private ManifestVersionSupport() {
    }

    static void requireSupported(TaskExecutionManifest manifest) {
        if (manifest == null || manifest.manifestVersion() == null
                || manifest.manifestVersion() != TaskExecutionManifest.CURRENT_MANIFEST_VERSION
                && manifest.manifestVersion() != TaskExecutionManifest.PREVIOUS_MANIFEST_VERSION) {
            throw new RunnerExecutionException(
                    "INVALID_MANIFEST",
                    "Runner 只支持 manifestVersion "
                            + TaskExecutionManifest.PREVIOUS_MANIFEST_VERSION + " 或 "
                            + TaskExecutionManifest.CURRENT_MANIFEST_VERSION,
                    null
            );
        }
        if (manifest.manifestVersion() == TaskExecutionManifest.PREVIOUS_MANIFEST_VERSION
                && requiresVersion10(manifest)) {
            throw new RunnerExecutionException(
                    "INVALID_MANIFEST",
                    "SPATIAL_SERVICE_INPUT 必须使用 manifestVersion 10",
                    null
            );
        }
    }

    private static boolean requiresVersion10(TaskExecutionManifest manifest) {
        return manifest.task() != null
                && manifest.task().definition() != null
                && manifest.task().definition().nodes() != null
                && manifest.task().definition().nodes().stream().anyMatch(node ->
                node != null && node.nodeType() == CanvasNodeType.SPATIAL_SERVICE_INPUT);
    }
}
