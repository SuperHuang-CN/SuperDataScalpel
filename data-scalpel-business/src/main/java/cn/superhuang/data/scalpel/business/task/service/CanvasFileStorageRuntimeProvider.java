package cn.superhuang.data.scalpel.business.task.service;

/**
 * Admin-owned adapter for the private file-dataset object store.
 *
 * <p>The Business module owns only this execution-facing port and never depends on Admin
 * configuration classes.</p>
 */
public interface CanvasFileStorageRuntimeProvider {

    CanvasTaskRunManifest.RuntimeFileStorage runtimeStorage();

    /** Resolves a storage-relative key to the exact key visible to an external Runner. */
    String resolveObjectKey(String objectKey);
}
