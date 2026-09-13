package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

@JsonClassDescription("批处理窗口计算配置；只接受 BOUNDED 来源，保留来源表的全部行与字段，并按 functions 顺序追加分析字段。所有分区、排序和函数表达式都只读取进入节点时的原始来源字段，不能引用同节点刚生成的窗口字段。")

public record WindowConfiguration(
        @JsonPropertyDescription("执行窗口计算的上游 Canvas 逻辑表名；必须引用进入本节点前已经存在的 BOUNDED 表。")
        String sourceTableName,
        @JsonPropertyDescription("窗口结果的 Canvas 逻辑表名；必须与进入节点时已有的所有表名不同，成功后作为新表加入表集合。")
        String outputTableName,
        @JsonPropertyDescription("窗口分区字段列表；允许空数组，表示把全部输入记录作为一个分区。字段必须存在、互不重复且不能是 Geometry。")
        List<String> partitionByColumns,
        @JsonPropertyDescription("窗口内部的多字段排序规则；至少一项，字段不能重复或使用 Geometry。数组顺序决定比较优先级，完全并列时不添加隐式稳定终结字段。该排序只影响窗口计算，不保证结果表或最终 Sink 的物理行顺序。")
        List<SortField> orderBy,
        @JsonPropertyDescription("按数组顺序追加到全部来源字段之后的窗口函数；必须包含 1 至 100 项。输出字段名不能与来源字段或其他窗口输出重复，每一项只能读取原始来源字段。")
        List<WindowFunctionItem> functions
) {
    public WindowConfiguration {
        partitionByColumns = partitionByColumns == null ? null : List.copyOf(partitionByColumns);
        orderBy = orderBy == null ? null : List.copyOf(orderBy);
        functions = functions == null ? null : List.copyOf(functions);
    }
}
