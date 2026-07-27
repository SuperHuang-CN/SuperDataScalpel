package cn.superhuang.data.scalpel.dispatcher.backend;

import java.util.List;

public record BackendReadiness(boolean ready, List<String> issues) {
    public BackendReadiness {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public static BackendReadiness up() { return new BackendReadiness(true, List.of()); }
    public static BackendReadiness down(String issue) { return new BackendReadiness(false, List.of(issue)); }
}
