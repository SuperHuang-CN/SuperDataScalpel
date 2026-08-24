package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record JsonExtractConfiguration(List<JsonExtractOperation> operations) {
    public JsonExtractConfiguration {
        operations = operations == null ? null : List.copyOf(operations);
    }
    public JsonExtractConfiguration(String sourceTableName, String outputTableName, String sourceColumnName,
                                    List<JsonExtraction> extractions, JsonExtractFailureStrategy failureStrategy) {
        this(List.of(new JsonExtractOperation(ProcessorOperation.LEGACY_OPERATION_ID, sourceTableName,
                new ProcessorOutput.CreateNewTable(outputTableName), sourceColumnName, extractions, failureStrategy)));
    }
    private JsonExtractOperation single() { return operations == null || operations.size() != 1 ? null : operations.getFirst(); }
    public String sourceTableName() { return single() == null ? null : single().sourceTableName(); }
    public String outputTableName() { return single() == null || single().output() == null ? null : single().output().outputTableName(); }
    public String sourceColumnName() { return single() == null ? null : single().sourceColumnName(); }
    public List<JsonExtraction> extractions() { return single() == null ? null : single().extractions(); }
    public JsonExtractFailureStrategy failureStrategy() { return single() == null ? null : single().failureStrategy(); }
}
