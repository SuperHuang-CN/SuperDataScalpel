package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.datascalpel.taskengine.contract.TaskExecutionManifest;

final class ManifestVersionSupport {
    private ManifestVersionSupport() {
    }

    static void requireSupported(TaskExecutionManifest manifest) {
        if (manifest == null || manifest.manifestVersion() == null
                || manifest.manifestVersion() != TaskExecutionManifest.CURRENT_MANIFEST_VERSION) {
            throw new RunnerExecutionException(
                    "INVALID_MANIFEST",
                    "Runner 只支持 manifestVersion " + TaskExecutionManifest.CURRENT_MANIFEST_VERSION,
                    null
            );
        }
    }
}
