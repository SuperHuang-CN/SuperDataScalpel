package cn.superhuang.datascalpel.sdk;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 按单次读取设置 JDBC 分片、批量获取行数与查询超时。
 * @apiGroup 读取调优
 * @apiExample var options = JdbcReadOptions.builder()
 *       .fetchSize(1000).queryTimeoutSeconds(300).build();
 *   var rows = context.models().read("source_model", options);
 * @apiNote 未配置项沿用 Spark/JDBC 默认值；不能覆盖平台连接地址、驱动或凭据。
 */
public final class JdbcReadOptions {
    private static final int MAX_PARTITIONS = 256;
    private static final int MAX_FETCH_SIZE = 1_000_000;
    private static final int MAX_QUERY_TIMEOUT_SECONDS = 86_400;
    private static final JdbcReadOptions DEFAULTS = new JdbcReadOptions(null, null, null, Map.of());
    private static final Map<String, String> SUPPORTED_OPTIONS = Map.of(
            "pushdownpredicate", "pushDownPredicate",
            "pushdownaggregate", "pushDownAggregate",
            "pushdownlimit", "pushDownLimit",
            "pushdownoffset", "pushDownOffset",
            "pushdowntablesample", "pushDownTableSample",
            "pushdownjoin", "pushDownJoin",
            "prefertimestampntz", "preferTimestampNTZ",
            "sessioninitstatement", "sessionInitStatement"
    );
    private static final Set<String> BOOLEAN_OPTIONS = Set.of(
            "pushdownpredicate", "pushdownaggregate", "pushdownlimit", "pushdownoffset",
            "pushdowntablesample", "pushdownjoin", "prefertimestampntz"
    );

    private final Partitioning partitioning;
    private final Integer fetchSize;
    private final Integer queryTimeoutSeconds;
    private final Map<String, String> options;

    private JdbcReadOptions(
            Partitioning partitioning,
            Integer fetchSize,
            Integer queryTimeoutSeconds,
            Map<String, String> options
    ) {
        this.partitioning = partitioning;
        this.fetchSize = fetchSize;
        this.queryTimeoutSeconds = queryTimeoutSeconds;
        this.options = Collections.unmodifiableMap(new LinkedHashMap<>(options));
    }

    /**
     * 取得默认读取选项，不额外覆盖 Spark 配置。
     */
    public static JdbcReadOptions defaults() {
        return DEFAULTS;
    }

    /**
     * 开始配置本次读取选项。
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 查看分片设置，未配置时为空。
     */
    public Optional<Partitioning> partitioning() {
        return Optional.ofNullable(partitioning);
    }

    /**
     * 查看批量获取行数，未配置时为空。
     */
    public Optional<Integer> fetchSize() {
        return Optional.ofNullable(fetchSize);
    }

    /**
     * 查看查询超时秒数，未配置时为空。
     */
    public Optional<Integer> queryTimeoutSeconds() {
        return Optional.ofNullable(queryTimeoutSeconds);
    }

    /**
     * 查看额外选项，返回不可修改的映射。
     */
    public Map<String, String> options() {
        return options;
    }

    /**
     * 判断是否没有配置任何读取选项。
     */
    public boolean isDefault() {
        return partitioning == null && fetchSize == null && queryTimeoutSeconds == null && options.isEmpty();
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof JdbcReadOptions other)) return false;
        return Objects.equals(partitioning, other.partitioning)
                && Objects.equals(fetchSize, other.fetchSize)
                && Objects.equals(queryTimeoutSeconds, other.queryTimeoutSeconds)
                && options.equals(other.options);
    }

    @Override
    public int hashCode() {
        return Objects.hash(partitioning, fetchSize, queryTimeoutSeconds, options);
    }

    @Override
    public String toString() {
        return "JdbcReadOptions[partitioning=" + partitioning + ", fetchSize=" + fetchSize
                + ", queryTimeoutSeconds=" + queryTimeoutSeconds + ", options=" + options + "]";
    }

    /**
     * JDBC 源端分片配置；分片数为 1～256。
     * @param column 用于分片的数值、日期或时间戳列。
     * @param lowerBound 划分分片用的下界，不用于过滤数据。
     * @param upperBound 划分分片用的上界，不用于过滤数据。
     * @param numPartitions 并行分片数，1～256。
     */
    public record Partitioning(String column, String lowerBound, String upperBound, int numPartitions) {
        public Partitioning {
            column = required(column, "partition column");
            lowerBound = required(lowerBound, "partition lower bound");
            upperBound = required(upperBound, "partition upper bound");
            if (numPartitions < 1 || numPartitions > MAX_PARTITIONS) {
                throw new IllegalArgumentException("JDBC partition count must be between 1 and " + MAX_PARTITIONS);
            }
        }
    }

    /**
     * 逐项配置读取参数，每个配置项只能设置一次。
     */
    public static final class Builder {
        private Partitioning partitioning;
        private Integer fetchSize;
        private Integer queryTimeoutSeconds;
        private final LinkedHashMap<String, String> options = new LinkedHashMap<>();

        private Builder() {
        }

        /**
         * 按列并行读取；上下界用于划分分片，不是结果过滤条件。
         * @param column 用于分片的数值、日期或时间戳列。
         * @param lowerBound 划分分片用的下界，不用于过滤数据。
         * @param upperBound 划分分片用的上界，不用于过滤数据。
         * @param numPartitions 并行分片数，1～256。
         */
        public Builder partitionBy(String column, String lowerBound, String upperBound, int numPartitions) {
            if (partitioning != null) {
                throw new IllegalStateException("JDBC partitioning has already been configured");
            }
            partitioning = new Partitioning(column, lowerBound, upperBound, numPartitions);
            return this;
        }

        /**
         * 设置每批获取行数，范围 0～1,000,000；0 使用驱动默认值。
         * @param value 每批获取行数，0～1,000,000。
         */
        public Builder fetchSize(int value) {
            if (value < 0 || value > MAX_FETCH_SIZE) {
                throw new IllegalArgumentException("JDBC fetch size must be between 0 and " + MAX_FETCH_SIZE);
            }
            if (fetchSize != null) {
                throw new IllegalStateException("JDBC fetch size has already been configured");
            }
            fetchSize = value;
            return this;
        }

        /**
         * 设置查询超时秒数，范围 0～86,400；0 表示不限制。
         * @param value 超时秒数，0～86,400。
         */
        public Builder queryTimeoutSeconds(int value) {
            if (value < 0 || value > MAX_QUERY_TIMEOUT_SECONDS) {
                throw new IllegalArgumentException(
                        "JDBC query timeout must be between 0 and " + MAX_QUERY_TIMEOUT_SECONDS + " seconds");
            }
            if (queryTimeoutSeconds != null) {
                throw new IllegalStateException("JDBC query timeout has already been configured");
            }
            queryTimeoutSeconds = value;
            return this;
        }

        /**
         * 设置白名单内的 Spark JDBC 读取选项，拒绝连接凭据等平台控制项。
         * @param name 读取选项名：pushDownPredicate、pushDownAggregate、pushDownLimit、pushDownOffset、pushDownTableSample、pushDownJoin、preferTimestampNTZ 或 sessionInitStatement。
         * @param value 非空的选项值；布尔选项填写 true 或 false，sessionInitStatement 填写会话初始化 SQL。
         */
        public Builder option(String name, String value) {
            String normalized = required(name, "JDBC read option name").toLowerCase(Locale.ROOT);
            String canonical = SUPPORTED_OPTIONS.get(normalized);
            if (canonical == null) {
                throw new IllegalArgumentException("Unsupported or platform-controlled JDBC read option: " + name);
            }
            value = required(value, "JDBC read option value");
            if (BOOLEAN_OPTIONS.contains(normalized)
                    && !("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value))) {
                throw new IllegalArgumentException("JDBC read option " + canonical + " must be true or false");
            }
            if ("sessioninitstatement".equals(normalized) && value.isBlank()) {
                throw new IllegalArgumentException("JDBC read option sessionInitStatement must not be blank");
            }
            if (options.putIfAbsent(normalized, value) != null) {
                throw new IllegalArgumentException("Duplicate JDBC read option: " + name);
            }
            return this;
        }

        /**
         * 生成不可变读取配置。
         */
        public JdbcReadOptions build() {
            LinkedHashMap<String, String> canonicalOptions = new LinkedHashMap<>();
            options.forEach((normalized, value) -> canonicalOptions.put(SUPPORTED_OPTIONS.get(normalized), value));
            if (partitioning == null && fetchSize == null && queryTimeoutSeconds == null && canonicalOptions.isEmpty()) {
                return defaults();
            }
            return new JdbcReadOptions(partitioning, fetchSize, queryTimeoutSeconds, canonicalOptions);
        }
    }

    private static String required(String value, String label) {
        Objects.requireNonNull(value, label);
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.indexOf('\0') >= 0
                || normalized.indexOf('\r') >= 0 || normalized.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("Invalid " + label);
        }
        return normalized;
    }
}
