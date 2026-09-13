package cn.superhuang.data.scalpel.business.system.configuration.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/** Supported storage and validation types for system configuration values. */
@Schema(description = "系统配置值类型：STRING 保留文本；INTEGER 规范化为十进制整数；BOOLEAN 规范化为小写 true 或 false。")
public enum SystemConfigurationValueType {
    STRING {
        @Override
        public String normalize(String rawValue) {
            if (rawValue == null || rawValue.isBlank()) {
                throw new IllegalArgumentException("配置值不能为空");
            }
            return rawValue;
        }
    },
    INTEGER {
        @Override
        public String normalize(String rawValue) {
            try {
                return Integer.toString(Integer.parseInt(requireValue(rawValue)));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("配置值必须是整数", exception);
            }
        }
    },
    BOOLEAN {
        @Override
        public String normalize(String rawValue) {
            String value = requireValue(rawValue);
            if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                throw new IllegalArgumentException("配置值必须是 true 或 false");
            }
            return Boolean.toString(Boolean.parseBoolean(value));
        }
    };

    public abstract String normalize(String rawValue);

    private static String requireValue(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException("配置值不能为空");
        }
        return rawValue;
    }
}
