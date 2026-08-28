package cn.superhuang.datascalpel.sdk.testkit;

import cn.superhuang.datascalpel.sdk.JdbcReadOptions;

import java.util.Objects;

/** One model read requested by user job code in a local TestKit run. */
public record TestModelReadCall(String bindingName, JdbcReadOptions options) {
    public TestModelReadCall {
        bindingName = require(bindingName, "bindingName");
        options = Objects.requireNonNull(options, "options");
    }

    private static String require(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        return value.trim();
    }
}
