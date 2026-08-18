package cn.superhuang.data.scalpel.contract.execution;

public record SparkConfigurationEntry(String name, String value) {
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
