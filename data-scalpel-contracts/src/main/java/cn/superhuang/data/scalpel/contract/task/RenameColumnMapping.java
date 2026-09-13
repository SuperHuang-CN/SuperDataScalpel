package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonClassDescription("RENAME 操作中的一项原子字段改名；所有映射均引用原始来源 Schema 并同时生效，数组顺序不形成链式重命名。")
public record RenameColumnMapping(
        @JsonPropertyDescription("原始来源字段名；必须存在且在本次映射数组中唯一。即使另一项映射先生成了某个名称，也不能把该中间名称作为来源。")
        String sourceColumnName,
        @JsonPropertyDescription("最终字段名；与所有其他映射结果及未重命名字段合并后必须唯一。等于 sourceColumnName 时不改变字段，只产生冗余映射警告。")
        String targetColumnName
) {
}
