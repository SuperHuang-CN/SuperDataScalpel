package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

@JsonClassDescription("对一张逻辑表执行一次 JSON 提取；完整保留来源字段，再按 extractions 顺序追加 nullable 标量字段。缺失 Path 或 JSON null 始终输出 SQL NULL；畸形 JSON 和类型转换失败由 failureStrategy 决定失败或置 NULL。")
public record JsonExtractOperation(
        @JsonPropertyDescription("当前操作的稳定 UUID，在本节点内唯一，用于关联配置、校验问题和字段血缘；不能使用空值或任意业务名称替代。")
        String operationId,
        @JsonPropertyDescription("进入本节点前已经存在的上游 Canvas 逻辑表名；同一节点内每张来源表最多配置一次。")
        String sourceTableName,
        @JsonPropertyDescription("结果表处理方式。REPLACE_SOURCE 替换来源 Map 项且不能改表名；CREATE_NEW_TABLE 保留来源并要求提供不冲突的新表名。")
        ProcessorOutput output,
        @JsonPropertyDescription("必填的 JSON 文本来源字段名；必须存在且平台类型为 STRING。SQL NULL 输入会使所有新增字段为 NULL。")
        String sourceColumnName,
        @JsonPropertyDescription("必填的有序提取数组，1 至 100 项；数组顺序就是新增字段顺序。每个输出名必须与全部来源字段和其他提取项唯一。")
        List<JsonExtraction> extractions,
        @JsonPropertyDescription("必填失败策略：ERROR 使用 parse_json 和 variant_get，畸形 JSON 或目标类型转换失败时节点失败；SET_NULL 使用 try_parse_json 和 try_variant_get，在失败位置输出 NULL。两者都不会跳过记录，Path 不存在都返回 NULL。")
        JsonExtractFailureStrategy failureStrategy
) implements ProcessorOperation {
    public JsonExtractOperation {
        extractions = extractions == null ? null : List.copyOf(extractions);
    }
}
