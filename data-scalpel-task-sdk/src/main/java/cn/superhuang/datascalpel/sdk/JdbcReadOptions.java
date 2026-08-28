package cn.superhuang.datascalpel.sdk;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Safe per-read Spark JDBC options. Connection details and source identity remain platform-owned.
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

    public static JdbcReadOptions defaults() {
        return DEFAULTS;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Optional<Partitioning> partitioning() {
        return Optional.ofNullable(partitioning);
    }

    public Optional<Integer> fetchSize() {
        return Optional.ofNullable(fetchSize);
    }

    public Optional<Integer> queryTimeoutSeconds() {
        return Optional.ofNullable(queryTimeoutSeconds);
    }

    public Map<String, String> options() {
        return options;
    }

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

    public static final class Builder {
        private Partitioning partitioning;
        private Integer fetchSize;
        private Integer queryTimeoutSeconds;
        private final LinkedHashMap<String, String> options = new LinkedHashMap<>();

        private Builder() {
        }

        public Builder partitionBy(String column, String lowerBound, String upperBound, int numPartitions) {
            if (partitioning != null) {
                throw new IllegalStateException("JDBC partitioning has already been configured");
            }
            partitioning = new Partitioning(column, lowerBound, upperBound, numPartitions);
            return this;
        }

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
