package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonClassDescription("排序、窗口、去重或 Top N 使用的一项显式字段顺序；direction 与 nullOrdering 均必填。所在数组的先后顺序决定多字段比较优先级，但排序完全并列时平台不添加隐式稳定终结字段。")
public record SortField(
        @JsonPropertyDescription("参与排序的来源字段名；必须存在，具体操作通常禁止重复字段和 Geometry。")
        String columnName,
        @JsonPropertyDescription("必填排序方向：ASC 升序，DESC 降序。")
        SortDirection direction,
        @JsonPropertyDescription("必填的 NULL 位置：FIRST 将 NULL 放在非 NULL 值之前，LAST 将 NULL 放在非 NULL 值之后；不依赖数据库或 Spark 默认值。")
        NullOrdering nullOrdering
) {
}
