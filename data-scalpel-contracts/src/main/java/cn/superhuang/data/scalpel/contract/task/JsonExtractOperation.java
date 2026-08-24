package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record JsonExtractOperation(
        String operationId,
        String sourceTableName,
        ProcessorOutput output,
        String sourceColumnName,
        List<JsonExtraction> extractions,
        JsonExtractFailureStrategy failureStrategy
) implements ProcessorOperation {
    public JsonExtractOperation {
        extractions = extractions == null ? null : List.copyOf(extractions);
    }
}
