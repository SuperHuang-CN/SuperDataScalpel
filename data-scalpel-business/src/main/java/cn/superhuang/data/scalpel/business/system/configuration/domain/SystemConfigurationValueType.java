package cn.superhuang.data.scalpel.business.system.configuration.domain;

/** Supported storage and validation types for system configuration values. */
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
