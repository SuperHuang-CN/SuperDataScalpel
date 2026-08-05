package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record JsonExtractConfiguration(
        String sourceTableName,
        String outputTableName,
        String sourceColumnName,
        List<JsonExtraction> extractions,
        JsonExtractFailureStrategy failureStrategy
) {
    public JsonExtractConfiguration {
        extractions = extractions == null ? null : List.copyOf(extractions);
    }
}
