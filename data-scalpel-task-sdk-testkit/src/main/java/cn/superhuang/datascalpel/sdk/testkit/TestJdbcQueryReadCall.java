package cn.superhuang.datascalpel.sdk.testkit;

import cn.superhuang.datascalpel.sdk.JdbcReadOptions;

import java.util.Objects;

public record TestJdbcQueryReadCall(String bindingName, String sql, JdbcReadOptions options) {
    public TestJdbcQueryReadCall {
        bindingName = required(bindingName, "bindingName");
        sql = required(sql, "sql");
        options = Objects.requireNonNull(options, "options");
    }

    private static String required(String value, String label) {
        Objects.requireNonNull(value, label);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return normalized;
    }
}
