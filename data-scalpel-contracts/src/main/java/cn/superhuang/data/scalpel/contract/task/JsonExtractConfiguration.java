package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

@JsonClassDescription("支持 BATCH 和 STREAMING 的 JSON 标量提取配置；对一个或多个不同上游表分别从单个 STRING 字段解析 JSON，并按 Spark VARIANT Path 追加最多 100 个类型化标量字段。它不推断 Schema、不展开数组，也不生成 STRUCT、ARRAY、MAP 或 GEOMETRY。")
public record JsonExtractConfiguration(
        @JsonPropertyDescription("独立处理不同来源表的操作数组，至少一项；operationId 和 sourceTableName 在节点内都必须唯一。各项只能读取进入本节点时已有的上游表，不能在同一节点内继续读取其他操作刚生成的结果。")
        List<JsonExtractOperation> operations
) {
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
