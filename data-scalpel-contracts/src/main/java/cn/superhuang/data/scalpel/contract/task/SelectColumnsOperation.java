package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

@JsonClassDescription("对一张逻辑表执行一次有序字段投影；只保留 columns 指定字段并按数组顺序形成输出 Schema，不支持通配符、表达式或隐式字段。若保留来源事件时间字段则连同 Watermark 继承，否则两者一起清除。")
public record SelectColumnsOperation(
        @JsonPropertyDescription("当前操作的稳定 UUID，在本节点内唯一，用于关联配置、校验问题和字段血缘；不能使用空值或任意业务名称替代。")
        String operationId,
        @JsonPropertyDescription("进入本节点前已经存在的上游 Canvas 逻辑表名；同一节点内每张来源表最多配置一次。")
        String sourceTableName,
        @JsonPropertyDescription("结果表处理方式。REPLACE_SOURCE 替换来源 Map 项且不能改表名；CREATE_NEW_TABLE 保留来源并要求提供不冲突的新表名。")
        ProcessorOutput output,
        @JsonPropertyDescription("必填的来源字段名数组，至少一项；每项必须按原始名称精确存在且不得重复，数组顺序就是最终输出字段顺序。")
        List<String> columns
) implements ProcessorOperation {
    public SelectColumnsOperation {
        columns = columns == null ? null : List.copyOf(columns);
    }
}
