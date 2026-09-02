package cn.superhuang.data.scalpel.contract.task;

import java.util.List;
import java.util.UUID;

/** Spark-free compiler response. jarBytes is populated only for a successful compilation. */
public record SparkJarSourceCompilationResponse(
        UUID requestId,
        boolean successful,
        long durationMs,
        String sourceSha256,
        String jarSha256,
        byte[] jarBytes,
        List<SparkJarSourceDiagnostic> diagnostics
) {
    public SparkJarSourceCompilationResponse {
        jarBytes = jarBytes == null ? null : jarBytes.clone();
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    @Override
    public byte[] jarBytes() {
        return jarBytes == null ? null : jarBytes.clone();
    }
}
