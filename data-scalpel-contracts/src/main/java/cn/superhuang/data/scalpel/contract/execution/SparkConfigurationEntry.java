package cn.superhuang.data.scalpel.contract.execution;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
public record SparkConfigurationEntry(
        @JsonPropertyDescription("经过平台白名单校验的 Spark 配置键。")
        String name,
        @JsonPropertyDescription("传给 Spark 的配置值；不得包含换行或平台凭据。")
        String value
) {
    public SparkConfigurationEntry {
        name = require(name, 500, "Spark 配置名");
        value = require(value, 2000, "Spark 配置值");
    }

    private static String require(String value, int maxLength, String label) {
        if (value == null || value.isBlank() || value.length() > maxLength
                || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException(label + "无效");
        }
        return value.trim();
    }
}
