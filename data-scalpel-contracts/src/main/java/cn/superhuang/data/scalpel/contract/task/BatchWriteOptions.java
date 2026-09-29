package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonClassDescription("可选批处理原子写入策略；整个对象缺失保持旧直接分区提交。对象存在表示使用中间表与单目标事务，需要 CREATE/DROP 权限；实时任务不支持。")
public record BatchWriteOptions(
        @JsonPropertyDescription("OVERWRITE 的目标字段删除条件；null 表示全表覆盖，非 null 必须是有效的非空结构化条件。条件字段须参与映射，输入全部属于此范围，否则删除前失败。非 OVERWRITE 必须为 null。")
        CanvasFilterCondition overwriteCondition,
        @JsonPropertyDescription("是否允许原子 OVERWRITE 空输入清空指定范围；默认 false，空输入时失败且保留旧数据。非 OVERWRITE 必须为 false。")
        boolean allowEmptyOverwrite
) { }
