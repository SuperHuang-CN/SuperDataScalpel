package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record TrackSplitExpression(String expression, List<TrackFieldWindowBinding> bindings, Boolean enabled) {
    public TrackSplitExpression {
        bindings = bindings == null ? List.of() : List.copyOf(bindings);
    }
    public TrackSplitExpression(String expression, List<TrackFieldWindowBinding> bindings) {
        this(expression, bindings, null);
    }
    public boolean active() { return !Boolean.FALSE.equals(enabled); }
}
