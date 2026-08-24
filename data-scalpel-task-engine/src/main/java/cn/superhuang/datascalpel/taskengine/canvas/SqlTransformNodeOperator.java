package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasDatasetKind;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasSqlTransformLimits;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.SqlTransformConfiguration;
import cn.superhuang.data.scalpel.contract.task.SqlTransformNodeDefinition;
import cn.superhuang.datascalpel.taskengine.contract.CanvasNodeCategory;
import cn.superhuang.datascalpel.taskengine.spark.SedonaSparkSupport;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import cn.superhuang.datascalpel.taskengine.spark.SparkTypeMapper;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.catalyst.analysis.UnresolvedRelation;
import org.apache.spark.sql.catalyst.analysis.UnresolvedTableValuedFunction;
import org.apache.spark.sql.catalyst.plans.logical.Command;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.ParsedStatement;
import org.apache.spark.sql.catalyst.plans.logical.UnresolvedWith;
import org.apache.spark.sql.catalyst.trees.TreeNode;
import scala.Tuple3;
import scala.jdk.javaapi.CollectionConverters;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Executes one read-only Spark SQL query over the complete current Canvas table map.
 *
 * <p>Every invocation receives a fresh child session. Local temp views therefore never
 * cross a Canvas node boundary, while the analyzed result plan is rebound to the caller
 * session before those views are removed.</p>
 */
public final class SqlTransformNodeOperator implements CanvasNodeOperator {

    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.SQL_TRANSFORM;
    }

    @Override
    public CanvasNodeCategory category() {
        return CanvasNodeCategory.PROCESSOR;
    }

    @Override
    public Set<CanvasExecutionMode> supportedModes() {
        return Set.of(CanvasExecutionMode.BATCH);
    }

    @Override
    public CanvasNodeOperationResult apply(
            CanvasNodeDefinition definition,
            Map<String, SparkCanvasTable> inputs,
            CanvasNodeOperationContext context
    ) {
        if (!(definition instanceof SqlTransformNodeDefinition node)) {
            throw new IllegalArgumentException("SQL_TRANSFORM operator received " + definition.nodeType());
        }
        List<CanvasTableSchema> inputSchemas = CanvasNodeSupport.schemas(inputs);
        SqlTransformConfiguration configuration = node.configuration();
        if (configuration == null) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }

        CanvasNodeIssueSink issues = context.issues();
        CanvasNodeSupport.required(
                configuration.outputTableName(), "请输入输出逻辑表名", "configuration.outputTableName", issues);
        if (configuration.sql().isBlank()) {
            issues.error("SQL_TRANSFORM_QUERY_REQUIRED", "请输入 SELECT 查询", "configuration.sql");
        } else if (configuration.sql().length() > CanvasSqlTransformLimits.MAX_SQL_LENGTH) {
            issues.error(
                    "SQL_TRANSFORM_QUERY_TOO_LONG",
                    "SQL 不能超过 " + CanvasSqlTransformLimits.MAX_SQL_LENGTH + " 个字符",
                    "configuration.sql"
            );
        } else if (!startsWithReadOnlyQuery(configuration.sql())) {
            issues.error(
                    "SQL_TRANSFORM_STATEMENT_NOT_ALLOWED",
                    "只允许单条 SELECT 或 WITH ... SELECT 查询",
                    "configuration.sql"
            );
        }
        if (!CanvasNodeSupport.blank(configuration.outputTableName())
                && inputs.containsKey(configuration.outputTableName())) {
            issues.error(
                    "DUPLICATE_TABLE_NAME",
                    "输出表名已存在：" + configuration.outputTableName(),
                    "configuration.outputTableName"
            );
        }
        if (issues.hasErrors()) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }

        org.apache.spark.sql.classic.SparkSession scopedSession;
        try {
            scopedSession = classic(SedonaSparkSupport.childSession(context.sparkSession()));
        } catch (RuntimeException exception) {
            issues.error("SQL_TRANSFORM_SESSION_UNAVAILABLE", "SQL 执行会话不可用", "configuration.sql");
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }

        LogicalPlan parsed;
        try {
            parsed = scopedSession.sessionState().sqlParser().parsePlan(configuration.sql());
        } catch (Exception exception) {
            issues.error("SQL_TRANSFORM_PARSE_ERROR", "SQL 语法无法解析", "configuration.sql");
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }
        SqlPolicyViolation violation = validateReadOnlyPlan(parsed, inputs.keySet());
        if (violation != null) {
            issues.error(violation.code(), violation.message(), "configuration.sql");
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }

        List<String> registeredViews = new ArrayList<>();
        try {
            for (Map.Entry<String, SparkCanvasTable> entry : inputs.entrySet()) {
                Dataset<Row> source = rebound(entry.getValue().dataset(), scopedSession);
                // Dataset#createTempView parses the supplied name. Quote it here so a Canvas
                // code such as "order.items" remains one local-view identifier instead of
                // being treated as a catalog-qualified relation. The SQL side uses the same
                // escaped identifier, while Catalog#dropTempView receives the raw stored name.
                source.createTempView(quoteIdentifier(entry.getKey()));
                registeredViews.add(entry.getKey());
            }
            Dataset<Row> scopedResult = scopedSession.sql(configuration.sql());
            LogicalPlan analyzed = classic(scopedResult).queryExecution().analyzed();
            Dataset<Row> result = rebound(analyzed, classic(context.sparkSession()));
            List<CanvasColumnSchema> columns = outputColumns(result, issues);
            if (issues.hasErrors()) {
                return CanvasNodeOperationResult.invalid(inputSchemas);
            }
            CanvasTableSchema outputSchema = new CanvasTableSchema(
                    configuration.outputTableName(),
                    null,
                    columns,
                    CanvasDatasetKind.BOUNDED,
                    null,
                    null
            );
            Map<String, SparkCanvasTable> output = new LinkedHashMap<>(inputs);
            output.put(outputSchema.name(), new SparkCanvasTable(outputSchema, result));
            return CanvasNodeOperationResult.propagated(output, CanvasNodeSupport.schemas(output));
        } catch (Exception exception) {
            issues.error(
                    "SQL_TRANSFORM_ANALYSIS_ERROR",
                    "SQL 无法针对当前上游表结构解析",
                    "configuration.sql"
            );
            return CanvasNodeOperationResult.invalid(inputSchemas);
        } finally {
            Collections.reverse(registeredViews);
            for (String identifier : registeredViews) {
                try {
                    scopedSession.catalog().dropTempView(identifier);
                } catch (RuntimeException ignored) {
                    // The child session is intentionally disposable. Cleanup must not mask SQL diagnostics.
                }
            }
        }
    }

    private static List<CanvasColumnSchema> outputColumns(
            Dataset<Row> result,
            CanvasNodeIssueSink issues
    ) {
        List<CanvasColumnSchema> columns;
        try {
            columns = SparkTypeMapper.fromStructType(result.schema(), List.of());
        } catch (RuntimeException exception) {
            issues.error(
                    "SQL_TRANSFORM_UNSUPPORTED_OUTPUT_TYPE",
                    "SQL 输出包含平台不支持或缺少稳定元数据的字段类型",
                    "configuration.sql"
            );
            return List.of();
        }
        Set<String> names = new LinkedHashSet<>();
        for (int index = 0; index < columns.size(); index++) {
            CanvasColumnSchema column = columns.get(index);
            String path = "configuration.sql";
            if (CanvasNodeSupport.blank(column.name())) {
                issues.error("SQL_TRANSFORM_OUTPUT_COLUMN_REQUIRED", "SQL 输出字段名不能为空", path);
            } else if (!names.add(column.name())) {
                issues.error(
                        "DUPLICATE_COLUMN_NAME",
                        "SQL 输出字段名重复：" + column.name(),
                        path
                );
            }
        }
        if (columns.isEmpty()) {
            issues.error("SQL_TRANSFORM_OUTPUT_REQUIRED", "SQL 查询必须产生至少一个字段", "configuration.sql");
        }
        return columns;
    }

    private static SqlPolicyViolation validateReadOnlyPlan(
            LogicalPlan parsed,
            Set<String> inputTableNames
    ) {
        Set<String> allowedRelations = new LinkedHashSet<>(inputTableNames);
        for (TreeNode<?> node : tree(parsed)) {
            if (node instanceof UnresolvedWith with) {
                for (Tuple3<String, ?, ?> relation : CollectionConverters.asJava(with.cteRelations())) {
                    allowedRelations.add(relation._1());
                }
            }
        }
        for (TreeNode<?> node : tree(parsed)) {
            if (node instanceof Command || node instanceof ParsedStatement) {
                return new SqlPolicyViolation(
                        "SQL_TRANSFORM_STATEMENT_NOT_ALLOWED",
                        "只允许单条只读查询，不能执行命令或写入语句"
                );
            }
            if (node instanceof UnresolvedTableValuedFunction) {
                return new SqlPolicyViolation(
                        "SQL_TRANSFORM_RELATION_NOT_ALLOWED",
                        "SQL 只能引用当前 Canvas 上游表，暂不支持表值函数"
                );
            }
            if (node instanceof UnresolvedRelation relation) {
                List<String> parts = List.copyOf(CollectionConverters.asJava(relation.multipartIdentifier()));
                if (parts.size() != 1 || !allowedRelations.contains(parts.getFirst())) {
                    return new SqlPolicyViolation(
                            "SQL_TRANSFORM_RELATION_NOT_ALLOWED",
                            "SQL 只能引用当前 Canvas 上游表或本查询中的 CTE"
                    );
                }
            }
        }
        return null;
    }

    private static List<TreeNode<?>> tree(TreeNode<?> root) {
        List<TreeNode<?>> nodes = new ArrayList<>();
        Set<TreeNode<?>> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayDeque<TreeNode<?>> pending = new ArrayDeque<>();
        pending.add(root);
        while (!pending.isEmpty()) {
            TreeNode<?> current = pending.removeFirst();
            if (!visited.add(current)) continue;
            nodes.add(current);
            CollectionConverters.asJava(current.children()).forEach(child -> pending.addLast(child));
            CollectionConverters.asJava(current.innerChildren()).forEach(child -> pending.addLast(child));
        }
        return nodes;
    }

    /**
     * Reject commands before Catalyst analysis without trying to interpret the SQL body.
     * The parser and logical-plan policy below remain the authoritative protection against
     * multi-statement input and unsupported relations.
     */
    private static boolean startsWithReadOnlyQuery(String sql) {
        int index = 0;
        while (index < sql.length()) {
            while (index < sql.length() && Character.isWhitespace(sql.charAt(index))) index++;
            if (index + 1 < sql.length() && sql.charAt(index) == '-'
                    && sql.charAt(index + 1) == '-') {
                int newline = sql.indexOf('\n', index + 2);
                index = newline < 0 ? sql.length() : newline + 1;
                continue;
            }
            if (index + 1 < sql.length() && sql.charAt(index) == '/'
                    && sql.charAt(index + 1) == '*') {
                int end = sql.indexOf("*/", index + 2);
                if (end < 0) return false;
                index = end + 2;
                continue;
            }
            break;
        }
        return startsWithKeyword(sql, index, "SELECT") || startsWithKeyword(sql, index, "WITH");
    }

    private static boolean startsWithKeyword(String sql, int start, String keyword) {
        if (start + keyword.length() > sql.length()
                || !sql.regionMatches(true, start, keyword, 0, keyword.length())) {
            return false;
        }
        int next = start + keyword.length();
        return next == sql.length() || !Character.isLetterOrDigit(sql.charAt(next))
                && sql.charAt(next) != '_';
    }

    private static String quoteIdentifier(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }

    private static Dataset<Row> rebound(
            Dataset<Row> dataset,
            org.apache.spark.sql.classic.SparkSession session
    ) {
        return rebound(classic(dataset).queryExecution().analyzed(), session);
    }

    private static Dataset<Row> rebound(
            LogicalPlan plan,
            org.apache.spark.sql.classic.SparkSession session
    ) {
        return org.apache.spark.sql.classic.Dataset.ofRows(session, plan);
    }

    private static org.apache.spark.sql.classic.Dataset<Row> classic(Dataset<Row> dataset) {
        if (dataset instanceof org.apache.spark.sql.classic.Dataset<Row> classic) {
            return classic;
        }
        throw new IllegalStateException("Canvas SQL only supports the Spark classic Dataset runtime");
    }

    private static org.apache.spark.sql.classic.SparkSession classic(SparkSession session) {
        if (session instanceof org.apache.spark.sql.classic.SparkSession classic) {
            return classic;
        }
        throw new IllegalStateException("Canvas SQL only supports the Spark classic Session runtime");
    }

    private record SqlPolicyViolation(String code, String message) {
    }
}
