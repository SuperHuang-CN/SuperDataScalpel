package cn.superhuang.data.scalpel.dispatcher.backend;

import java.util.List;

public record BackendReadiness(boolean ready, List<String> issues, boolean checking) {
    public BackendReadiness(boolean ready, List<String> issues) { this(ready, issues, false); }
    public BackendReadiness {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public static BackendReadiness up() { return new BackendReadiness(true, List.of()); }
    public static BackendReadiness down(String issue) { return new BackendReadiness(false, List.of(issue)); }
    public static BackendReadiness pending() {
        return new BackendReadiness(false, List.of("目标就绪状态检查中，请稍后刷新"), true);
    }
}
