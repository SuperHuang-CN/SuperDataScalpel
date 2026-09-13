package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

@JsonClassDescription("对一张逻辑表执行一次字段类型转换；所有 casts 基于同一来源 Schema 通过一次投影在原字段位置生效，不能让后续转换读取前一项转换结果。输出继承 Origin、有界性、事件时间和 Watermark。")
public record TypeCastOperation(
        @JsonPropertyDescription("当前操作的稳定 UUID，在本节点内唯一，用于关联配置、校验问题和字段血缘；不能使用空值或任意业务名称替代。")
        String operationId,
        @JsonPropertyDescription("进入本节点前已经存在的上游 Canvas 逻辑表名；同一节点内每张来源表最多配置一次。")
        String sourceTableName,
        @JsonPropertyDescription("结果表处理方式。REPLACE_SOURCE 替换来源 Map 项且不能改表名；CREATE_NEW_TABLE 保留来源并要求提供不冲突的新表名。")
        ProcessorOutput output,
        @JsonPropertyDescription("必填且至少一项的字段转换数组；每个来源字段最多配置一次。Geometry 不能作为来源或目标，流任务的事件时间字段不能转换。")
        List<ColumnTypeCast> casts
) implements ProcessorOperation {
    public TypeCastOperation {
        casts = casts == null ? null : List.copyOf(casts);
    }
}
