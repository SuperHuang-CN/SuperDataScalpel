package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

@JsonClassDescription("按指定字段的 SQL NULL 状态删除整行；ANY_NULL 在任一字段为 NULL 时删除，ALL_NULL 仅在全部字段为 NULL 时删除。允许在流任务中用它删除事件时间为空的记录。")
public record DropNullRowsRule(
        @JsonPropertyDescription("用于判断的来源字段名数组，至少一项且不得重复；只检查 SQL NULL，不匹配空字符串、NaN 或零值。")
        List<String> columnNames,
        @JsonPropertyDescription("必填的 NULL 组合方式：ANY_NULL 表示任一指定字段为 NULL 即删除，ALL_NULL 表示全部指定字段同时为 NULL 才删除。")
        NullMatchMode matchMode
) implements NullHandlingRule {
    public DropNullRowsRule {
        columnNames = columnNames == null ? null : List.copyOf(columnNames);
    }
}
