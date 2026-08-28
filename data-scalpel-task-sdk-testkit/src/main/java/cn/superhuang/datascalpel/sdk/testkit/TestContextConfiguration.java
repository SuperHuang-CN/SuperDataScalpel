package cn.superhuang.datascalpel.sdk.testkit;

import cn.superhuang.datascalpel.sdk.JdbcTableIdentifier;
import cn.superhuang.datascalpel.sdk.SparkJobIdentity;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.StructType;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.nio.file.Path;

final class TestContextConfiguration {
    final SparkSession sparkSession;
    final SparkJobIdentity identity;
    final Map<String, String> parameters;
    final int captureRowLimit;
    final Map<String, ModelBinding> models;
    final Map<String, JdbcBinding> jdbc;

    private TestContextConfiguration(Builder builder) {
        sparkSession = builder.sparkSession;
        identity = builder.identity;
        parameters = Collections.unmodifiableMap(new LinkedHashMap<>(builder.parameters));
        captureRowLimit = builder.captureRowLimit;
        models = copyModels(builder.models);
        jdbc = copyJdbc(builder.jdbc);
    }

    static final class Builder {
        private SparkSession sparkSession;
        private SparkJobIdentity identity;
        private final LinkedHashMap<String, String> parameters = new LinkedHashMap<>();
        private int captureRowLimit = 10_000;
        private final LinkedHashMap<String, MutableModelBinding> models = new LinkedHashMap<>();
        private final LinkedHashMap<String, MutableJdbcBinding> jdbc = new LinkedHashMap<>();

        Builder sparkSession(SparkSession value) {
            if (value == null) throw new IllegalArgumentException("sparkSession must not be null");
            if (sparkSession != null && sparkSession != value) {
                throw new IllegalArgumentException("All test Datasets must use the configured SparkSession");
            }
            sparkSession = value;
            return this;
        }

        Builder identity(SparkJobIdentity value) { identity = value; return this; }

        Builder parameter(String name, String value) {
            name = required(name, "parameter name");
            if (value == null) throw new IllegalArgumentException("parameter value must not be null");
            if (parameters.putIfAbsent(name, value) != null) {
                throw new IllegalArgumentException("Duplicate parameter: " + name);
            }
            return this;
        }

        Builder captureRowLimit(int value) {
            if (value < 1) throw new IllegalArgumentException("captureRowLimit must be positive");
            captureRowLimit = value;
            return this;
        }

        Builder modelInput(String name, StructType schema, List<Row> rows) {
            MutableModelBinding binding = model(name);
            if (binding.input != null) throw new IllegalArgumentException("Duplicate model input: " + name);
            binding.input = InputSpec.rows(schema, rows);
            return this;
        }

        Builder modelInput(String name, Dataset<Row> dataset) {
            MutableModelBinding binding = model(name);
            if (binding.input != null) throw new IllegalArgumentException("Duplicate model input: " + name);
            inferSpark(dataset);
            binding.input = InputSpec.dataset(dataset);
            return this;
        }

        Builder modelInputParquet(String name, Path path, StructType expectedSchema) {
            MutableModelBinding binding = model(name);
            if (binding.input != null) throw new IllegalArgumentException("Duplicate model input: " + name);
            binding.input = InputSpec.parquet(path, expectedSchema);
            return this;
        }

        Builder modelOutput(String name, TestModelTarget target) {
            MutableModelBinding binding = model(name);
            if (binding.target != null) throw new IllegalArgumentException("Duplicate model output: " + name);
            binding.target = java.util.Objects.requireNonNull(target, "target");
            return this;
        }

        Builder jdbcTable(String name, JdbcTableIdentifier table, StructType schema, List<Row> rows) {
            MutableJdbcBinding binding = jdbc(name);
            if (binding.tables.putIfAbsent(java.util.Objects.requireNonNull(table), InputSpec.rows(schema, rows)) != null) {
                throw new IllegalArgumentException("Duplicate JDBC table: " + table);
            }
            return this;
        }

        Builder jdbcTable(String name, JdbcTableIdentifier table, Dataset<Row> dataset) {
            MutableJdbcBinding binding = jdbc(name);
            inferSpark(dataset);
            if (binding.tables.putIfAbsent(java.util.Objects.requireNonNull(table), InputSpec.dataset(dataset)) != null) {
                throw new IllegalArgumentException("Duplicate JDBC table: " + table);
            }
            return this;
        }

        Builder jdbcTableParquet(String name, JdbcTableIdentifier table, Path path, StructType expectedSchema) {
            MutableJdbcBinding binding = jdbc(name);
            if (binding.tables.putIfAbsent(java.util.Objects.requireNonNull(table),
                    InputSpec.parquet(path, expectedSchema)) != null) {
                throw new IllegalArgumentException("Duplicate JDBC table: " + table);
            }
            return this;
        }

        Builder jdbcQuery(String name, String sql, StructType schema, List<Row> rows) {
            MutableJdbcBinding binding = jdbc(name);
            String normalized = normalizedSql(sql);
            if (binding.queries.putIfAbsent(normalized, InputSpec.rows(schema, rows)) != null) {
                throw new IllegalArgumentException("Duplicate JDBC query: " + normalized);
            }
            return this;
        }

        Builder jdbcQuery(String name, String sql, Dataset<Row> dataset) {
            MutableJdbcBinding binding = jdbc(name);
            inferSpark(dataset);
            String normalized = normalizedSql(sql);
            if (binding.queries.putIfAbsent(normalized, InputSpec.dataset(dataset)) != null) {
                throw new IllegalArgumentException("Duplicate JDBC query: " + normalized);
            }
            return this;
        }

        Builder jdbcOutput(String name, JdbcTableIdentifier table, StructType schema) {
            MutableJdbcBinding binding = jdbc(name);
            table = java.util.Objects.requireNonNull(table, "table");
            schema = java.util.Objects.requireNonNull(schema, "schema");
            if (new LinkedHashSet<>(List.of(schema.fieldNames())).size() != schema.fields().length) {
                throw new IllegalArgumentException("JDBC target schema column names must be unique");
            }
            if (binding.targets.putIfAbsent(table, schema) != null) {
                throw new IllegalArgumentException("Duplicate JDBC output table: " + table);
            }
            return this;
        }

        TestContextConfiguration build() { return new TestContextConfiguration(this); }

        private MutableModelBinding model(String name) {
            return models.computeIfAbsent(required(name, "model binding name"), ignored -> new MutableModelBinding());
        }

        private MutableJdbcBinding jdbc(String name) {
            return jdbc.computeIfAbsent(required(name, "JDBC binding name"), ignored -> new MutableJdbcBinding());
        }

        private void inferSpark(Dataset<Row> dataset) {
            if (dataset == null) throw new IllegalArgumentException("dataset must not be null");
            sparkSession(dataset.sparkSession());
        }
    }

    record InputSpec(Dataset<Row> dataset, StructType schema, List<Row> rows, Path parquetPath) {
        static InputSpec dataset(Dataset<Row> value) {
            return new InputSpec(java.util.Objects.requireNonNull(value), null, List.of(), null);
        }

        static InputSpec rows(StructType schema, List<Row> values) {
            return new InputSpec(null, java.util.Objects.requireNonNull(schema), List.copyOf(values), null);
        }

        static InputSpec parquet(Path path, StructType expectedSchema) {
            return new InputSpec(null, java.util.Objects.requireNonNull(expectedSchema), List.of(),
                    java.util.Objects.requireNonNull(path, "path").toAbsolutePath().normalize());
        }

        Dataset<Row> materialize(SparkSession spark) {
            if (dataset != null) {
                if (dataset.sparkSession() != spark) {
                    throw new SparkJobTestException("TESTKIT_SPARK_SESSION_MISMATCH",
                            "Bound Dataset belongs to a different SparkSession");
                }
                return dataset;
            }
            if (parquetPath != null) {
                if (!java.nio.file.Files.isRegularFile(parquetPath)) {
                    throw new SparkJobTestException("TESTKIT_PARQUET_NOT_FOUND",
                            "Parquet sample file does not exist: " + parquetPath);
                }
                Dataset<Row> loaded;
                try {
                    loaded = spark.read().parquet(parquetPath.toString());
                } catch (RuntimeException exception) {
                    throw new SparkJobTestException("TESTKIT_PARQUET_READ_FAILED",
                            "Unable to read Parquet sample: " + parquetPath, exception);
                }
                if (!loaded.schema().equals(schema)) {
                    throw new SparkJobTestException("TESTKIT_PARQUET_SCHEMA_MISMATCH",
                            "Parquet schema does not match expected schema. expected="
                                    + schema.catalogString() + ", actual=" + loaded.schema().catalogString());
                }
                return loaded;
            }
            return spark.createDataFrame(rows, schema);
        }
    }

    record ModelBinding(InputSpec input, TestModelTarget target) { }
    record JdbcBinding(
            Map<JdbcTableIdentifier, InputSpec> tables,
            Map<String, InputSpec> queries,
            Map<JdbcTableIdentifier, StructType> targets
    ) { }

    private static final class MutableModelBinding {
        private InputSpec input;
        private TestModelTarget target;
    }

    private static final class MutableJdbcBinding {
        private final LinkedHashMap<JdbcTableIdentifier, InputSpec> tables = new LinkedHashMap<>();
        private final LinkedHashMap<String, InputSpec> queries = new LinkedHashMap<>();
        private final LinkedHashMap<JdbcTableIdentifier, StructType> targets = new LinkedHashMap<>();
    }

    private static Map<String, ModelBinding> copyModels(Map<String, MutableModelBinding> values) {
        LinkedHashMap<String, ModelBinding> result = new LinkedHashMap<>();
        values.forEach((name, value) -> result.put(name, new ModelBinding(value.input, value.target)));
        return Collections.unmodifiableMap(result);
    }

    private static Map<String, JdbcBinding> copyJdbc(Map<String, MutableJdbcBinding> values) {
        LinkedHashMap<String, JdbcBinding> result = new LinkedHashMap<>();
        values.forEach((name, value) -> result.put(name, new JdbcBinding(
                Collections.unmodifiableMap(new LinkedHashMap<>(value.tables)),
                Collections.unmodifiableMap(new LinkedHashMap<>(value.queries)),
                Collections.unmodifiableMap(new LinkedHashMap<>(value.targets)))));
        return Collections.unmodifiableMap(result);
    }

    static String normalizedSql(String value) {
        return required(value, "SQL").trim();
    }

    static String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " must not be blank");
        return value.trim();
    }
}
