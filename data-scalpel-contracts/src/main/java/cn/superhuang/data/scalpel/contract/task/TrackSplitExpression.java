package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

@JsonClassDescription("轨迹重建的受控 Spark SQL 单一布尔表达式。表达式为 true 时从当前观测前切分，false 或 NULL 不切分；不支持语句、注释、用户 OVER/WINDOW、非确定性函数或 Arcade API。")

public record TrackSplitExpression(
        @JsonPropertyDescription("最多 8192 字符的确定性 BOOLEAN/NULL 表达式；可引用入口字段和 bindings 名称，不能引用其他绑定。内容为空、不可解析或结果不是布尔值时编译失败。")
        String expression,
        @JsonPropertyDescription("最多 32 个命名窗口绑定；名称大小写不敏感唯一且不能覆盖来源字段，窗口只在同一轨迹和固定周期内取值。")
        List<TrackFieldWindowBinding> bindings,
        @JsonPropertyDescription("是否启用该分段条件；为空按启用处理。")
        Boolean enabled
) {
    public TrackSplitExpression {
        bindings = bindings == null ? List.of() : List.copyOf(bindings);
    }
    public TrackSplitExpression(String expression, List<TrackFieldWindowBinding> bindings) {
        this(expression, bindings, null);
    }
    public boolean active() { return !Boolean.FALSE.equals(enabled); }
}
