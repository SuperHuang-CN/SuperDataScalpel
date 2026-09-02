package cn.superhuang.datascalpel.taskengine.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Explicit maintenance tool for the checked-in Monaco Spark/SDK completion index. */
public final class SparkJavaApiIndexGenerator {
    private static final Map<Class<?>, String> TYPES = new LinkedHashMap<>();
    private static final Map<String, Set<String>> METHODS = new LinkedHashMap<>();

    static {
        TYPES.put(org.apache.spark.sql.Dataset.class, "Dataset");
        TYPES.put(org.apache.spark.sql.Column.class, "Column");
        TYPES.put(org.apache.spark.sql.RelationalGroupedDataset.class, "RelationalGroupedDataset");
        TYPES.put(org.apache.spark.sql.Row.class, "Row");
        TYPES.put(org.apache.spark.sql.SparkSession.class, "SparkSession");
        TYPES.put(org.apache.spark.sql.DataFrameReader.class, "DataFrameReader");
        TYPES.put(org.apache.spark.sql.DataFrameWriter.class, "DataFrameWriter");
        TYPES.put(org.apache.spark.sql.functions.class, "functions");
        TYPES.put(org.apache.spark.sql.expressions.Window.class, "Window");
        TYPES.put(org.apache.spark.sql.expressions.WindowSpec.class, "WindowSpec");
        TYPES.put(cn.superhuang.datascalpel.sdk.SparkJobContext.class, "SparkJobContext");
        TYPES.put(cn.superhuang.datascalpel.sdk.ModelResources.class, "ModelResources");
        TYPES.put(cn.superhuang.datascalpel.sdk.JdbcResources.class, "JdbcResources");
        TYPES.put(cn.superhuang.datascalpel.sdk.ModelWriteOperation.class, "ModelWriteOperation");

        METHODS.put("Dataset", Set.of("select", "selectExpr", "withColumn", "withColumnRenamed", "drop", "filter",
                "where", "join", "groupBy", "agg", "orderBy", "limit", "distinct", "dropDuplicates",
                "unionByName", "repartition", "coalesce", "cache", "col", "schema", "sparkSession"));
        METHODS.put("Column", Set.of("alias", "cast", "equalTo", "notEqual", "gt", "geq", "lt", "leq", "and",
                "or", "isNull", "isNotNull", "asc", "desc", "over"));
        METHODS.put("RelationalGroupedDataset", Set.of("agg", "count", "sum", "avg", "min", "max", "pivot"));
        METHODS.put("Row", Set.of("getAs", "isNullAt", "fieldIndex", "schema"));
        METHODS.put("SparkSession", Set.of("sql", "read", "table", "emptyDataFrame"));
        METHODS.put("DataFrameReader", Set.of("format", "option", "options", "schema", "load", "parquet", "json", "csv"));
        METHODS.put("DataFrameWriter", Set.of("format", "mode", "option", "options", "save", "parquet", "json", "csv"));
        METHODS.put("functions", Set.of("col", "lit", "when", "count", "sum", "avg", "min", "max", "concat",
                "coalesce", "struct", "array", "row_number", "rank", "dense_rank"));
        METHODS.put("Window", Set.of("partitionBy", "orderBy", "rowsBetween", "rangeBetween"));
        METHODS.put("WindowSpec", Set.of("partitionBy", "orderBy", "rowsBetween", "rangeBetween"));
        METHODS.put("SparkJobContext", Set.of("models", "jdbc", "observability", "spark", "parameter"));
        METHODS.put("ModelResources", Set.of("read", "write"));
        METHODS.put("JdbcResources", Set.of("readTable", "readQuery", "writeTable"));
        METHODS.put("ModelWriteOperation", Set.of("mode", "map", "mapSameName", "checkSchema", "execute"));
    }

    private SparkJavaApiIndexGenerator() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected output JSON path");
        List<ApiEntry> entries = new ArrayList<>();
        TYPES.forEach((type, owner) -> {
            for (Method method : type.getMethods()) {
                if (!Modifier.isPublic(method.getModifiers()) || method.isBridge() || method.isSynthetic()
                        || !METHODS.getOrDefault(owner, Set.of()).contains(method.getName())
                        || java.util.Arrays.stream(method.getParameterTypes()).anyMatch(SparkJavaApiIndexGenerator::scalaOnly)) {
                    continue;
                }
                entries.add(new ApiEntry(owner, method.getName(), signature(method), simple(method.getReturnType()),
                        summary(owner, method.getName()), snippet(method)));
            }
        });
        List<ApiEntry> sortedEntries = entries.stream().distinct().sorted(Comparator.comparing(ApiEntry::owner)
                .thenComparing(ApiEntry::name).thenComparing(ApiEntry::signature)).toList();
        Path output = Path.of(args[0]).toAbsolutePath().normalize();
        Files.createDirectories(output.getParent());
        new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValue(output.toFile(), sortedEntries);
    }

    private static boolean scalaOnly(Class<?> type) {
        return type.getName().startsWith("scala.") || type.getName().startsWith("scala$");
    }

    private static String signature(Method method) {
        StringBuilder result = new StringBuilder(method.getName()).append('(');
        Parameter[] parameters = method.getParameters();
        for (int index = 0; index < parameters.length; index++) {
            if (index > 0) result.append(", ");
            Class<?> type = parameters[index].getType();
            String typeName = method.isVarArgs() && index == parameters.length - 1
                    ? simple(type.getComponentType()) + "..." : simple(type);
            result.append(typeName).append(' ').append(parameterName(parameters[index], index));
        }
        return result.append(')').toString();
    }

    private static String snippet(Method method) {
        StringBuilder result = new StringBuilder(method.getName()).append('(');
        Parameter[] parameters = method.getParameters();
        for (int index = 0; index < parameters.length; index++) {
            if (index > 0) result.append(", ");
            result.append("${").append(index + 1).append(':').append(parameterName(parameters[index], index)).append('}');
        }
        return result.append(')').toString();
    }

    private static String parameterName(Parameter parameter, int index) {
        return parameter.isNamePresent() && !parameter.getName().matches("arg\\d+")
                ? parameter.getName() : "value" + (index + 1);
    }

    private static String simple(Class<?> type) {
        if (type == null) return "void";
        if (type.isArray()) return simple(type.getComponentType()) + "[]";
        String value = type.getSimpleName();
        return value == null || value.isBlank() ? type.getName() : value;
    }

    private static String summary(String owner, String name) {
        return switch (owner + "." + name) {
            case "Dataset.groupBy" -> "按字段分组并进入聚合操作。";
            case "Dataset.join" -> "连接两个Dataset。";
            case "Dataset.repartition" -> "调整Spark侧分区，不会改变JDBC源端分片。";
            case "Dataset.schema" -> "返回Dataset Schema，不触发Action。";
            case "ModelWriteOperation.checkSchema" -> "在本地TestKit中检查字段集合和Spark类型；生产运行不提前校验。";
            case "ModelWriteOperation.mapSameName" -> "将Dataset字段自动映射到同名目标模型字段。";
            default -> owner + " 的 " + name + " 方法（Spark 4.1.1 / DataScalpel Job API v1）。";
        };
    }

    private record ApiEntry(String owner, String name, String signature, String returnType,
                            String summary, String snippet) {
    }
}
