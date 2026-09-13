package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

@JsonClassDescription("对一张逻辑表执行一次字段脱敏；1 至 100 个不同字段通过一次投影原位替换，未配置字段透传。输出保持字段名称、顺序、类型和 nullable，并继承 Origin、有界性、事件时间和 Watermark；流任务禁止脱敏事件时间字段。")
public record MaskFieldsOperation(
        @JsonPropertyDescription("当前操作的稳定 UUID，在本节点内唯一，用于关联配置、校验问题和字段血缘；不能使用空值或任意业务名称替代。")
        String operationId,
        @JsonPropertyDescription("进入本节点前已经存在的上游 Canvas 逻辑表名；同一节点内每张来源表最多配置一次。")
        String sourceTableName,
        @JsonPropertyDescription("结果表处理方式。REPLACE_SOURCE 替换来源 Map 项且不能改表名；CREATE_NEW_TABLE 保留来源并要求提供不冲突的新表名。")
        ProcessorOutput output,
        @JsonPropertyDescription("必填的字段脱敏规则数组，1 至 100 项；同一字段只能出现一次。每项的 definition 是唯一执行依据，任何来源引用只用于展示和审计。")
        List<MaskFieldRule> fieldRules
) implements ProcessorOperation {
    public MaskFieldsOperation {
        fieldRules = fieldRules == null ? null : List.copyOf(fieldRules);
    }
}
