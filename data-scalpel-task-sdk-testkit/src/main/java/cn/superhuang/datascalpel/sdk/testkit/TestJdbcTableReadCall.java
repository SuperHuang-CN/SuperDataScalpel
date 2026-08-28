package cn.superhuang.datascalpel.sdk.testkit;

import cn.superhuang.datascalpel.sdk.JdbcReadOptions;
import cn.superhuang.datascalpel.sdk.JdbcTableIdentifier;

import java.util.Objects;

/** One JDBC table read requested by user job code in a local TestKit run. */
public record TestJdbcTableReadCall(
        String bindingName,
        JdbcTableIdentifier table,
        JdbcReadOptions options
) {
    public TestJdbcTableReadCall {
        if (bindingName == null || bindingName.isBlank()) {
            throw new IllegalArgumentException("bindingName is required");
        }
        bindingName = bindingName.trim();
        table = Objects.requireNonNull(table, "table");
        options = Objects.requireNonNull(options, "options");
    }
}
