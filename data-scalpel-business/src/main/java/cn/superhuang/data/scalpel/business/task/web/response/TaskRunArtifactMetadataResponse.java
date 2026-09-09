package cn.superhuang.data.scalpel.business.task.web.response;

/** Safe metadata used to decide whether a task-run artifact can be read in the browser. */
public record TaskRunArtifactMetadataResponse(
        String kind,
        String fileName,
        Availability availability,
        Long sizeBytes,
        boolean previewAvailable
) {
    public enum Availability {
        AVAILABLE,
        NOT_GENERATED,
        SIZE_UNAVAILABLE
    }
}
