package cn.superhuang.datascalpel.sdk.testkit;

import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class TestModelTarget {
    private final StructType schema;
    private final List<String> primaryKeyColumns;
    private final Set<String> omittableColumns;
    private final boolean external;

    private TestModelTarget(Builder builder) {
        schema = java.util.Objects.requireNonNull(builder.schema, "schema");
        List<String> names = Arrays.stream(schema.fields()).map(StructField::name).toList();
        if (new LinkedHashSet<>(names).size() != names.size()) {
            throw new IllegalArgumentException("Target model schema column names must be unique");
        }
        primaryKeyColumns = normalized(builder.primaryKeyColumns, "primaryKeyColumns");
        omittableColumns = Set.copyOf(normalized(builder.omittableColumns, "omittableColumns"));
        if (!names.containsAll(primaryKeyColumns) || !names.containsAll(omittableColumns)) {
            throw new IllegalArgumentException("Target model key and omittable columns must exist in schema");
        }
        external = builder.external;
    }

    public static Builder builder(StructType schema) {
        return new Builder(schema);
    }

    public StructType schema() { return schema; }
    public List<String> primaryKeyColumns() { return primaryKeyColumns; }
    public Set<String> omittableColumns() { return omittableColumns; }
    public boolean external() { return external; }

    List<String> requiredColumns() {
        return Arrays.stream(schema.fields())
                .filter(field -> !field.nullable() && !omittableColumns.contains(field.name()))
                .map(StructField::name)
                .toList();
    }

    public static final class Builder {
        private final StructType schema;
        private final List<String> primaryKeyColumns = new java.util.ArrayList<>();
        private final List<String> omittableColumns = new java.util.ArrayList<>();
        private boolean external;

        private Builder(StructType schema) { this.schema = schema; }

        public Builder primaryKeyColumns(String... values) {
            primaryKeyColumns.clear();
            if (values != null) primaryKeyColumns.addAll(Arrays.asList(values));
            return this;
        }

        public Builder omittableColumns(String... values) {
            omittableColumns.clear();
            if (values != null) omittableColumns.addAll(Arrays.asList(values));
            return this;
        }

        public Builder external(boolean value) { external = value; return this; }
        public TestModelTarget build() { return new TestModelTarget(this); }
    }

    private static List<String> normalized(List<String> values, String label) {
        List<String> result = values.stream().map(value -> {
            if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " must not contain blanks");
            return value.trim();
        }).toList();
        if (new LinkedHashSet<>(result).size() != result.size()) {
            throw new IllegalArgumentException(label + " must not contain duplicates");
        }
        return result;
    }
}
