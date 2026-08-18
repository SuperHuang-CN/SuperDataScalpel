package cn.superhuang.data.scalpel.dialect.query.lineage;

import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Pattern;

/** Static, best-effort SQL AST analyzer. It never participates in SQL authorization or execution. */
public final class SqlLineageAnalyzer {

    private static final Set<String> AGGREGATES = Set.of(
            "AVG", "COUNT", "GROUP_CONCAT", "LISTAGG", "MAX", "MIN", "STDDEV", "STDDEV_POP",
            "STDDEV_SAMP", "STRING_AGG", "SUM", "VARIANCE", "VAR_POP", "VAR_SAMP"
    );
    private static final Pattern STRING_LITERAL = Pattern.compile("'(?:''|[^'])*'");
    private static final Pattern NUMBER_LITERAL = Pattern.compile("(?<![A-Za-z_$])[-+]?\\d+(?:\\.\\d+)?(?:[Ee][-+]?\\d+)?");

    private SqlLineageAnalyzer() {
    }

    public static SqlLineageAnalysis analyze(SqlLineageAnalysisRequest request) {
        Objects.requireNonNull(request, "request");
        Context context = new Context(request);
        try {
            Statement statement = CCJSqlParserUtil.parse(request.sql());
            if (!(statement instanceof Select select)) {
                return unavailable("SQL_AST_NOT_SELECT", "SQL AST 不是 SELECT 查询");
            }
            QueryView root = context.analyzeSelect(select, null, new LinkedHashMap<>());
            context.warnUnusedRelations();
            SqlLineageAnalysis.AnalysisStatus status = context.unavailable
                    ? SqlLineageAnalysis.AnalysisStatus.UNAVAILABLE
                    : context.partial || root.outputs().stream().anyMatch(output -> !output.reliable())
                    ? SqlLineageAnalysis.AnalysisStatus.PARTIAL
                    : SqlLineageAnalysis.AnalysisStatus.COMPLETE;
            return new SqlLineageAnalysis(
                    status,
                    !context.tableDiscoveryUnreliable,
                    context.tableReferences,
                    root.outputs(),
                    deduplicate(context.fieldUsages),
                    context.warnings
            );
        } catch (JSQLParserException | RuntimeException exception) {
            return unavailable("SQL_AST_UNAVAILABLE", "SQL AST 无法可靠解析：" + safeMessage(exception));
        }
    }

    private static SqlLineageAnalysis unavailable(String code, String message) {
        return new SqlLineageAnalysis(
                SqlLineageAnalysis.AnalysisStatus.UNAVAILABLE, false, List.of(), List.of(), List.of(),
                List.of(new SqlLineageAnalysis.Warning(code, message, null))
        );
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private static List<SqlLineageAnalysis.FieldUsage> deduplicate(List<SqlLineageAnalysis.FieldUsage> usages) {
        return new ArrayList<>(new LinkedHashSet<>(usages));
    }

    private static final class Context {
        private final SqlLineageAnalysisRequest request;
        private final IdentifierRules rules;
        private final List<SqlLineageAnalysis.TableReference> tableReferences = new ArrayList<>();
        private final List<SqlLineageAnalysis.FieldUsage> fieldUsages = new ArrayList<>();
        private final List<SqlLineageAnalysis.Warning> warnings = new ArrayList<>();
        private final Set<String> usedRelationKeys = new HashSet<>();
        private int joinOrdinal;
        private boolean partial;
        private boolean unavailable;
        private boolean tableDiscoveryUnreliable;

        private Context(SqlLineageAnalysisRequest request) {
            this.request = request;
            this.rules = new IdentifierRules(request.databaseType());
        }

        private QueryView analyzeSelect(Select select, Scope outer, Map<String, QueryView> inheritedCtes) {
            Map<String, QueryView> ctes = new LinkedHashMap<>(inheritedCtes);
            if (select.getWithItemsList() != null) {
                for (WithItem<?> item : select.getWithItemsList()) {
                    String cteKey = rules.fold(item.getUnquotedAliasName());
                    if (item.isRecursive()) {
                        warn("RECURSIVE_CTE_PARTIAL", "递归 CTE 暂不能可靠生成字段血缘", null);
                        partial = true;
                        ctes.put(cteKey, new QueryView(List.of()));
                    }
                    ParenthesedSelect body = item.getSelect();
                    if (body == null) {
                        warn("CTE_UNSUPPORTED", "当前 CTE 不是可分析的 SELECT", null);
                        partial = true;
                        continue;
                    }
                    QueryView view = analyzeSelect(body.getSelect(), outer, ctes);
                    if (item.getWithItemList() != null && !item.getWithItemList().isEmpty()) {
                        view = renameView(view, item.getWithItemList());
                    }
                    ctes.put(cteKey, view);
                }
            }
            QueryView result;
            if (select instanceof PlainSelect plain) {
                result = analyzePlain(plain, outer, ctes);
            } else if (select instanceof SetOperationList set) {
                result = analyzeSet(set, outer, ctes);
            } else if (select instanceof ParenthesedSelect parenthesed) {
                result = analyzeSelect(parenthesed.getSelect(), outer, ctes);
            } else {
                warn("SELECT_SHAPE_UNSUPPORTED", "当前 SELECT 结构暂不能可靠生成字段血缘", null);
                partial = true;
                result = new QueryView(List.of());
            }
            if (!(select instanceof PlainSelect)) {
                addOrderUsages(select.getOrderByElements(), result, outer, "local-sql:order");
            }
            return result;
        }

        private QueryView renameView(QueryView view, List<SelectItem<?>> aliases) {
            if (aliases.size() != view.outputs().size()) {
                warn("CTE_COLUMN_COUNT_MISMATCH", "CTE 字段别名数量与查询输出数量不一致", null);
                partial = true;
                return view;
            }
            List<SqlLineageAnalysis.Output> renamed = new ArrayList<>();
            for (int index = 0; index < view.outputs().size(); index++) {
                SqlLineageAnalysis.Output output = view.outputs().get(index);
                renamed.add(copyOutput(output, aliases.get(index).toString()));
            }
            return new QueryView(renamed);
        }

        private QueryView analyzeSet(SetOperationList set, Scope outer, Map<String, QueryView> ctes) {
            List<Select> selects = set.getSelects();
            if (selects == null || selects.isEmpty()) return new QueryView(List.of());
            List<QueryView> branches = selects.stream().map(branch -> analyzeSelect(branch, outer, ctes)).toList();
            int width = branches.getFirst().outputs().size();
            if (branches.stream().anyMatch(branch -> branch.outputs().size() != width)) {
                warn("SET_OUTPUT_COUNT_MISMATCH", "集合运算各分支输出字段数量不一致", null);
                partial = true;
                return branches.getFirst();
            }
            List<SqlLineageAnalysis.Output> outputs = new ArrayList<>();
            for (int index = 0; index < width; index++) {
                List<SqlLineageAnalysis.Output> parts = new ArrayList<>();
                for (QueryView branch : branches) parts.add(branch.outputs().get(index));
                boolean reliable = parts.stream().allMatch(SqlLineageAnalysis.Output::reliable);
                LinkedHashSet<SqlLineageAnalysis.FieldReference> sources = new LinkedHashSet<>();
                parts.forEach(output -> sources.addAll(output.sources()));
                SqlLineageAnalysis.OutputKind kind = reliable ? mergeKinds(parts) : SqlLineageAnalysis.OutputKind.UNKNOWN;
                outputs.add(new SqlLineageAnalysis.Output(
                        index + 1, parts.getFirst().outputName(), kind, reliable,
                        sha256("set\0" + parts.stream().map(SqlLineageAnalysis.Output::expressionFingerprint).toList()),
                        reliable ? List.copyOf(sources) : List.of()
                ));
            }
            return new QueryView(outputs);
        }

        private SqlLineageAnalysis.OutputKind mergeKinds(List<SqlLineageAnalysis.Output> parts) {
            if (parts.stream().anyMatch(output -> output.kind() == SqlLineageAnalysis.OutputKind.UNKNOWN)) {
                return SqlLineageAnalysis.OutputKind.UNKNOWN;
            }
            if (parts.stream().anyMatch(output -> output.kind() == SqlLineageAnalysis.OutputKind.AGGREGATED)) {
                return SqlLineageAnalysis.OutputKind.AGGREGATED;
            }
            SqlLineageAnalysis.OutputKind first = parts.getFirst().kind();
            return parts.stream().allMatch(output -> output.kind() == first)
                    ? first : SqlLineageAnalysis.OutputKind.CALCULATED;
        }

        private QueryView analyzePlain(PlainSelect plain, Scope outer, Map<String, QueryView> ctes) {
            Scope scope = new Scope(outer, ctes);
            if (plain.getWindowDefinitions() != null && !plain.getWindowDefinitions().isEmpty()) {
                warn("NAMED_WINDOW_PARTIAL", "命名 WINDOW 的分区和排序用途暂不能可靠识别", null);
                partial = true;
            }
            addFromItem(scope, plain.getFromItem(), ctes, outer);
            if (plain.getJoins() != null) {
                for (Join join : plain.getJoins()) {
                    addFromItem(scope, join.getRightItem(), ctes, outer);
                    String nodeKey = "local-sql:join:" + (++joinOrdinal);
                    if (join.isNatural()) {
                        warn("NATURAL_JOIN_PARTIAL", "NATURAL JOIN 暂不能可靠识别连接字段", null);
                        partial = true;
                    }
                    if (join.getOnExpressions() != null) {
                        for (Expression expression : join.getOnExpressions()) {
                            addExpressionUsage(expression, scope, nodeKey, SqlLineageAnalysis.FieldUsageKind.JOIN_KEY);
                        }
                    }
                    if (join.getUsingColumns() != null) {
                        for (Column column : join.getUsingColumns()) addUsingUsages(column, scope, nodeKey);
                    }
                }
            }
            addExpressionUsage(plain.getWhere(), scope, "local-sql:where", SqlLineageAnalysis.FieldUsageKind.FILTER_CONDITION);
            addExpressionUsage(plain.getHaving(), scope, "local-sql:having", SqlLineageAnalysis.FieldUsageKind.FILTER_CONDITION);
            if (plain.getGroupBy() != null && plain.getGroupBy().getGroupByExpressionList() != null) {
                for (Object value : plain.getGroupBy().getGroupByExpressionList()) {
                    if (value instanceof Expression expression) {
                        addExpressionUsage(expression, scope, "local-sql:group", SqlLineageAnalysis.FieldUsageKind.GROUP_KEY);
                    }
                }
            }
            List<SqlLineageAnalysis.Output> outputs = new ArrayList<>();
            List<SelectItem<?>> items = plain.getSelectItems() == null ? List.of() : plain.getSelectItems();
            for (SelectItem<?> item : items) {
                Expression expression = item.getExpression();
                if (expression instanceof AllTableColumns allTableColumns) {
                    expandTableWildcard(outputs, allTableColumns, scope);
                } else if (expression instanceof AllColumns allColumns) {
                    expandWildcard(outputs, allColumns, scope);
                } else {
                    outputs.add(analyzeOutput(outputs.size() + 1, item, expression, scope));
                }
            }
            QueryView view = new QueryView(outputs);
            addOrderUsages(plain.getOrderByElements(), view, scope, "local-sql:order");
            return view;
        }

        private void addFromItem(Scope scope, FromItem item, Map<String, QueryView> ctes, Scope outer) {
            if (item == null) return;
            if (item.getPivot() != null || item.getUnPivot() != null) {
                warn("PIVOT_PARTIAL", "PIVOT/UNPIVOT 暂不能可靠生成字段血缘", null);
                partial = true;
            }
            if (item instanceof Table table) {
                String tableName = table.getUnquotedName();
                QueryView cte = table.getSchemaName() == null ? ctes.get(rules.fold(tableName)) : null;
                if (cte != null) {
                    scope.add(RelationView.derived(aliasOf(table, tableName), cte.outputs()));
                    return;
                }
                scope.add(resolveBaseTable(table));
            } else if (item instanceof ParenthesedSelect select) {
                QueryView query = analyzeSelect(
                        select.getSelect(), item instanceof LateralSubSelect ? scope : outer, ctes
                );
                scope.add(RelationView.derived(aliasOf(select, "derived"), query.outputs()));
            } else {
                warn("FROM_ITEM_UNSUPPORTED", "FROM 中存在暂不支持的表函数或方言结构", null);
                partial = true;
                tableDiscoveryUnreliable = true;
            }
        }

        private RelationView resolveBaseTable(Table table) {
            RawTable raw = rules.table(table, request.defaultCatalog(), request.defaultSchema());
            if (rules.isOracleDual(raw)) {
                tableReferences.add(new SqlLineageAnalysis.TableReference(
                        table.getFullyQualifiedName(), raw.identifier(), SqlLineageAnalysis.TableMatchStatus.SYSTEM, null));
                return RelationView.empty(aliasOf(table, raw.identifier().table()));
            }
            List<SqlLineageAnalysisRequest.Relation> matches = request.relations().stream()
                    .filter(relation -> rules.matches(raw, relation.table()))
                    .toList();
            if (matches.size() == 1) {
                SqlLineageAnalysisRequest.Relation relation = matches.getFirst();
                usedRelationKeys.add(relation.relationKey());
                tableReferences.add(new SqlLineageAnalysis.TableReference(
                        table.getFullyQualifiedName(), raw.identifier(), SqlLineageAnalysis.TableMatchStatus.MATCHED,
                        relation.relationKey()));
                return RelationView.base(aliasOf(table, raw.identifier().table()), relation);
            }
            SqlLineageAnalysis.TableMatchStatus status = matches.isEmpty()
                    ? SqlLineageAnalysis.TableMatchStatus.UNDECLARED : SqlLineageAnalysis.TableMatchStatus.AMBIGUOUS;
            tableReferences.add(new SqlLineageAnalysis.TableReference(
                    table.getFullyQualifiedName(), raw.identifier(), status, null));
            partial = true;
            return RelationView.unknown(aliasOf(table, raw.identifier().table()));
        }

        private SqlLineageAnalysis.Output analyzeOutput(int ordinal, SelectItem<?> item, Expression expression, Scope scope) {
            ExpressionEvidence evidence = expressionEvidence(expression, scope, ordinal);
            String outputName = item.getUnquotedAliasName();
            if (outputName == null && expression instanceof Column column) outputName = column.getUnquotedColumnName();
            SqlLineageAnalysis.OutputKind kind = classify(expression, evidence);
            boolean reliable = evidence.reliable && kind != SqlLineageAnalysis.OutputKind.UNKNOWN;
            if (!reliable) {
                partial = true;
                warn("OUTPUT_LINEAGE_UNKNOWN", "无法可靠识别第 " + ordinal + " 个输出字段的值来源", ordinal);
            }
            addWindowUsages(expression, scope, ordinal);
            return new SqlLineageAnalysis.Output(
                    ordinal, outputName, reliable ? kind : SqlLineageAnalysis.OutputKind.UNKNOWN, reliable,
                    fingerprint(expression), reliable ? List.copyOf(evidence.sources) : List.of()
            );
        }

        private SqlLineageAnalysis.OutputKind classify(Expression expression, ExpressionEvidence evidence) {
            if (isNullExpression(expression)) return SqlLineageAnalysis.OutputKind.NULL_FILLED;
            if (isLiteral(expression)) return SqlLineageAnalysis.OutputKind.CONSTANT;
            if (!evidence.reliable || evidence.sources.isEmpty()) return SqlLineageAnalysis.OutputKind.UNKNOWN;
            if (evidence.aggregate || evidence.inheritedKind == SqlLineageAnalysis.OutputKind.AGGREGATED) {
                return SqlLineageAnalysis.OutputKind.AGGREGATED;
            }
            if (evidence.inheritedKind == SqlLineageAnalysis.OutputKind.CALCULATED) {
                return SqlLineageAnalysis.OutputKind.CALCULATED;
            }
            return expression instanceof Column
                    ? SqlLineageAnalysis.OutputKind.DIRECT : SqlLineageAnalysis.OutputKind.CALCULATED;
        }

        private SqlLineageAnalysis.OutputKind mergeKind(
                SqlLineageAnalysis.OutputKind current,
                SqlLineageAnalysis.OutputKind incoming
        ) {
            if (incoming == null) return current;
            if (current == null) return incoming;
            if (current == SqlLineageAnalysis.OutputKind.UNKNOWN || incoming == SqlLineageAnalysis.OutputKind.UNKNOWN) {
                return SqlLineageAnalysis.OutputKind.UNKNOWN;
            }
            if (current == SqlLineageAnalysis.OutputKind.AGGREGATED || incoming == SqlLineageAnalysis.OutputKind.AGGREGATED) {
                return SqlLineageAnalysis.OutputKind.AGGREGATED;
            }
            if (current == SqlLineageAnalysis.OutputKind.CALCULATED || incoming == SqlLineageAnalysis.OutputKind.CALCULATED) {
                return SqlLineageAnalysis.OutputKind.CALCULATED;
            }
            return current;
        }

        private void expandWildcard(List<SqlLineageAnalysis.Output> outputs, AllColumns wildcard, Scope scope) {
            if (hasWildcardModifiers(wildcard)) {
                wildcardUnknown(outputs, "带 EXCEPT/REPLACE 的通配符暂不能可靠展开");
                return;
            }
            if (scope.relations.isEmpty() || scope.relations.stream().anyMatch(relation -> !relation.fieldsReliable)) {
                wildcardUnknown(outputs, "无法可靠展开 SELECT *");
                return;
            }
            for (RelationView relation : scope.relations) appendRelationFields(outputs, relation);
        }

        private void expandTableWildcard(List<SqlLineageAnalysis.Output> outputs, AllTableColumns wildcard, Scope scope) {
            if (hasWildcardModifiers(wildcard)) {
                wildcardUnknown(outputs, "带 EXCEPT/REPLACE 的通配符暂不能可靠展开");
                return;
            }
            RelationView relation = scope.findRelation(wildcard.getTable().getUnquotedName());
            if (relation == null || !relation.fieldsReliable) {
                wildcardUnknown(outputs, "无法可靠展开 " + wildcard);
                return;
            }
            appendRelationFields(outputs, relation);
        }

        private void appendRelationFields(List<SqlLineageAnalysis.Output> outputs, RelationView relation) {
            for (ViewField field : relation.fields) {
                int ordinal = outputs.size() + 1;
                boolean reliable = field.reliable && !field.sources.isEmpty();
                outputs.add(new SqlLineageAnalysis.Output(
                        ordinal, field.name, reliable ? field.kind : SqlLineageAnalysis.OutputKind.UNKNOWN,
                        reliable, sha256("wildcard\0" + relation.alias + '\0' + field.name),
                        reliable ? field.sources : List.of()
                ));
            }
        }

        private void wildcardUnknown(List<SqlLineageAnalysis.Output> outputs, String message) {
            partial = true;
            warn("WILDCARD_EXPANSION_UNKNOWN", message, outputs.size() + 1);
            outputs.add(new SqlLineageAnalysis.Output(
                    outputs.size() + 1, null, SqlLineageAnalysis.OutputKind.UNKNOWN, false,
                    sha256("wildcard-unknown"), List.of()));
        }

        private boolean hasWildcardModifiers(AllColumns wildcard) {
            return (wildcard.getExceptColumns() != null && !wildcard.getExceptColumns().isEmpty())
                    || (wildcard.getReplaceExpressions() != null && !wildcard.getReplaceExpressions().isEmpty());
        }

        private ExpressionEvidence expressionEvidence(Expression expression, Scope scope, Integer outputOrdinal) {
            ExpressionEvidence evidence = new ExpressionEvidence();
            if (expression == null) return evidence.unreliable();
            ExpressionVisitorAdapter<Void> visitor = new ExpressionVisitorAdapter<>() {
                @Override
                public <S> Void visit(Column column, S context) {
                    Resolution resolution = scope.resolve(column, rules);
                    if (!resolution.reliable) {
                        evidence.reliable = false;
                        warn("FIELD_REFERENCE_AMBIGUOUS", "字段引用无法唯一解析：" + column, outputOrdinal);
                    } else {
                        evidence.sources.addAll(resolution.sources);
                        evidence.inheritedKind = mergeKind(evidence.inheritedKind, resolution.kind);
                    }
                    return null;
                }

                @Override
                public <S> Void visit(Function function, S context) {
                    if (AGGREGATES.contains(function.getName().toUpperCase(Locale.ROOT))) evidence.aggregate = true;
                    return super.visit(function, context);
                }

                @Override
                public <S> Void visit(AnalyticExpression analytic, S context) {
                    if (analytic.getName() != null && AGGREGATES.contains(analytic.getName().toUpperCase(Locale.ROOT))) {
                        evidence.aggregate = true;
                    }
                    if (analytic.getExpression() != null) analytic.getExpression().accept(this, context);
                    if (analytic.getOffset() != null) analytic.getOffset().accept(this, context);
                    if (analytic.getDefaultValue() != null) analytic.getDefaultValue().accept(this, context);
                    if (analytic.getFilterExpression() != null) analytic.getFilterExpression().accept(this, context);
                    return null;
                }

                @Override
                public <S> Void visit(ParenthesedSelect select, S context) {
                    QueryView subquery = analyzeSelect(select.getSelect(), scope, scope.ctes);
                    if (subquery.outputs().size() != 1 || !subquery.outputs().getFirst().reliable()) {
                        evidence.reliable = false;
                    } else {
                        evidence.sources.addAll(subquery.outputs().getFirst().sources());
                        evidence.aggregate |= subquery.outputs().getFirst().kind() == SqlLineageAnalysis.OutputKind.AGGREGATED;
                        evidence.inheritedKind = mergeKind(
                                evidence.inheritedKind, subquery.outputs().getFirst().kind());
                    }
                    return null;
                }
            };
            expression.accept(visitor, null);
            return evidence;
        }

        private void addExpressionUsage(
                Expression expression,
                Scope scope,
                String nodeKey,
                SqlLineageAnalysis.FieldUsageKind kind
        ) {
            if (expression == null) return;
            ExpressionEvidence evidence = expressionEvidence(expression, scope, null);
            if (!evidence.reliable) {
                partial = true;
                warn("FIELD_USAGE_PARTIAL", "无法可靠识别 " + nodeKey + " 中的全部字段用途", null);
                return;
            }
            for (SqlLineageAnalysis.FieldReference source : evidence.sources) {
                fieldUsages.add(new SqlLineageAnalysis.FieldUsage(source, nodeKey, kind));
            }
        }

        private void addUsingUsages(Column column, Scope scope, String nodeKey) {
            List<SqlLineageAnalysis.FieldReference> sources = scope.resolveAllByName(column.getUnquotedColumnName(), rules);
            if (sources.size() < 2) {
                partial = true;
                warn("JOIN_USING_PARTIAL", "无法可靠识别 JOIN USING 字段：" + column, null);
                return;
            }
            for (SqlLineageAnalysis.FieldReference source : sources) {
                fieldUsages.add(new SqlLineageAnalysis.FieldUsage(
                        source, nodeKey, SqlLineageAnalysis.FieldUsageKind.JOIN_KEY));
            }
        }

        private void addOrderUsages(List<OrderByElement> elements, QueryView outputs, Scope scope, String nodeKey) {
            if (elements == null) return;
            for (OrderByElement order : elements) {
                Expression expression = order.getExpression();
                SqlLineageAnalysis.Output output = outputReference(expression, outputs);
                if (output != null && output.reliable()) {
                    for (SqlLineageAnalysis.FieldReference source : output.sources()) {
                        fieldUsages.add(new SqlLineageAnalysis.FieldUsage(
                                source, nodeKey, SqlLineageAnalysis.FieldUsageKind.SORT_KEY));
                    }
                } else {
                    addExpressionUsage(expression, scope, nodeKey, SqlLineageAnalysis.FieldUsageKind.SORT_KEY);
                }
            }
        }

        private SqlLineageAnalysis.Output outputReference(Expression expression, QueryView outputs) {
            if (expression instanceof net.sf.jsqlparser.expression.LongValue ordinal) {
                long value = ordinal.getValue();
                return value >= 1 && value <= outputs.outputs().size() ? outputs.outputs().get((int) value - 1) : null;
            }
            if (expression instanceof Column column && column.getTable() == null) {
                String name = rules.fold(column.getUnquotedColumnName());
                List<SqlLineageAnalysis.Output> matches = outputs.outputs().stream()
                        .filter(output -> output.outputName() != null && rules.fold(output.outputName()).equals(name)).toList();
                return matches.size() == 1 ? matches.getFirst() : null;
            }
            return null;
        }

        private void addWindowUsages(Expression expression, Scope scope, int outputOrdinal) {
            if (expression == null) return;
            expression.accept(new ExpressionVisitorAdapter<Void>() {
                @Override
                public <S> Void visit(AnalyticExpression analytic, S context) {
                    if (analytic.getPartitionExpressionList() != null) {
                        for (Object value : analytic.getPartitionExpressionList()) {
                            if (value instanceof Expression part) addExpressionUsage(
                                    part, scope, "local-sql:window:" + outputOrdinal + ":partition",
                                    SqlLineageAnalysis.FieldUsageKind.PARTITION_KEY);
                        }
                    }
                    if (analytic.getOrderByElements() != null) {
                        for (OrderByElement order : analytic.getOrderByElements()) addExpressionUsage(
                                order.getExpression(), scope, "local-sql:window:" + outputOrdinal + ":order",
                                SqlLineageAnalysis.FieldUsageKind.SORT_KEY);
                    }
                    return super.visit(analytic, context);
                }
            }, null);
        }

        private void warnUnusedRelations() {
            for (SqlLineageAnalysisRequest.Relation relation : request.relations()) {
                if (!usedRelationKeys.contains(relation.relationKey())) {
                    warn("DECLARED_INPUT_UNUSED", "已声明输入表未被 SQL AST 引用：" + relation.table().table(), null);
                }
            }
        }

        private void warn(String code, String message, Integer outputOrdinal) {
            SqlLineageAnalysis.Warning warning = new SqlLineageAnalysis.Warning(code, message, outputOrdinal);
            if (!warnings.contains(warning)) warnings.add(warning);
        }
    }

    private static final class Scope {
        private final Scope outer;
        private final Map<String, QueryView> ctes;
        private final List<RelationView> relations = new ArrayList<>();

        private Scope(Scope outer, Map<String, QueryView> ctes) {
            this.outer = outer;
            this.ctes = Map.copyOf(ctes);
        }

        private void add(RelationView relation) {
            relations.add(relation);
        }

        private RelationView findRelation(String alias) {
            if (alias == null) return null;
            List<RelationView> matches = relations.stream()
                    .filter(relation -> relation.alias.equalsIgnoreCase(unquote(alias))).toList();
            if (matches.size() == 1) return matches.getFirst();
            return outer == null ? null : outer.findRelation(alias);
        }

        private Resolution resolve(Column column, IdentifierRules rules) {
            String qualifier = column.getUnquotedTableName();
            if (qualifier != null && !qualifier.isBlank()) {
                RelationView relation = findRelation(qualifier);
                return relation == null ? Resolution.unknown() : relation.resolve(column.getColumnName(), rules);
            }
            List<Resolution> matches = relations.stream()
                    .map(relation -> relation.resolve(column.getColumnName(), rules))
                    .filter(resolution -> resolution.reliable && !resolution.sources.isEmpty()).toList();
            if (matches.size() == 1) return matches.getFirst();
            if (matches.isEmpty() && outer != null) return outer.resolve(column, rules);
            return Resolution.unknown();
        }

        private List<SqlLineageAnalysis.FieldReference> resolveAllByName(String name, IdentifierRules rules) {
            LinkedHashSet<SqlLineageAnalysis.FieldReference> result = new LinkedHashSet<>();
            for (RelationView relation : relations) {
                Resolution resolution = relation.resolve(name, rules);
                if (resolution.reliable) result.addAll(resolution.sources);
            }
            return List.copyOf(result);
        }
    }

    private static final class RelationView {
        private final String alias;
        private final List<ViewField> fields;
        private final boolean fieldsReliable;

        private RelationView(String alias, List<ViewField> fields, boolean fieldsReliable) {
            this.alias = unquote(alias);
            this.fields = fields;
            this.fieldsReliable = fieldsReliable;
        }

        private static RelationView base(String alias, SqlLineageAnalysisRequest.Relation relation) {
            List<ViewField> fields = relation.fields().stream()
                    .sorted(Comparator.comparingInt(SqlLineageAnalysisRequest.Field::ordinal))
                    .map(field -> new ViewField(
                            field.columnName(), SqlLineageAnalysis.OutputKind.DIRECT, true,
                            List.of(new SqlLineageAnalysis.FieldReference(relation.relationKey(), field.fieldKey()))))
                    .toList();
            return new RelationView(alias, fields, true);
        }

        private static RelationView derived(String alias, List<SqlLineageAnalysis.Output> outputs) {
            return new RelationView(alias, outputs.stream().map(output -> new ViewField(
                    output.outputName(), output.kind(), output.reliable(), output.sources())).toList(),
                    outputs.stream().allMatch(output -> output.outputName() != null));
        }

        private static RelationView empty(String alias) {
            return new RelationView(alias, List.of(), true);
        }

        private static RelationView unknown(String alias) {
            return new RelationView(alias, List.of(), false);
        }

        private Resolution resolve(String rawName, IdentifierRules rules) {
            List<ViewField> matches = fields.stream()
                    .filter(field -> field.name != null && rules.identifierMatches(rawName, field.name)).toList();
            if (matches.size() != 1 || !matches.getFirst().reliable) return Resolution.unknown();
            ViewField match = matches.getFirst();
            return new Resolution(match.sources, true, match.kind);
        }
    }

    private record ViewField(
            String name,
            SqlLineageAnalysis.OutputKind kind,
            boolean reliable,
            List<SqlLineageAnalysis.FieldReference> sources
    ) {
    }

    private record QueryView(List<SqlLineageAnalysis.Output> outputs) {
    }

    private static final class ExpressionEvidence {
        private final LinkedHashSet<SqlLineageAnalysis.FieldReference> sources = new LinkedHashSet<>();
        private boolean reliable = true;
        private boolean aggregate;
        private SqlLineageAnalysis.OutputKind inheritedKind;

        private ExpressionEvidence unreliable() {
            reliable = false;
            return this;
        }
    }

    private record Resolution(
            List<SqlLineageAnalysis.FieldReference> sources,
            boolean reliable,
            SqlLineageAnalysis.OutputKind kind
    ) {
        private static Resolution unknown() {
            return new Resolution(List.of(), false, SqlLineageAnalysis.OutputKind.UNKNOWN);
        }
    }

    private record RawTable(TableIdentifier identifier, boolean catalogQuoted, boolean schemaQuoted, boolean tableQuoted) {
    }

    private static final class IdentifierRules {
        private final String databaseType;

        private IdentifierRules(String databaseType) {
            this.databaseType = databaseType == null ? "" : databaseType.toUpperCase(Locale.ROOT);
        }

        private RawTable table(Table table, String defaultCatalog, String defaultSchema) {
            String catalog = table.getCatalogName();
            String schema = table.getSchemaName();
            if ((databaseType.equals("MYSQL") || databaseType.equals("CLICKHOUSE")) && catalog == null && schema != null) {
                catalog = schema;
                schema = null;
            }
            return new RawTable(
                    new TableIdentifier(
                            unquote(catalog == null ? defaultCatalog : catalog),
                            unquote(schema == null ? defaultSchema : schema),
                            unquote(table.getName())
                    ),
                    quoted(catalog), quoted(schema), quoted(table.getName())
            );
        }

        private boolean matches(RawTable raw, TableIdentifier candidate) {
            return partMatches(raw.identifier().catalog(), candidate.catalog(), raw.catalogQuoted())
                    && partMatches(raw.identifier().schema(), candidate.schema(), raw.schemaQuoted())
                    && partMatches(raw.identifier().table(), candidate.table(), raw.tableQuoted());
        }

        private boolean identifierMatches(String raw, String candidate) {
            return partMatches(unquote(raw), candidate, quoted(raw));
        }

        private boolean partMatches(String raw, String candidate, boolean quoted) {
            if (raw == null || candidate == null) return raw == null && candidate == null;
            return quoted ? raw.equals(candidate) : fold(raw).equals(fold(candidate));
        }

        private String fold(String value) {
            if (value == null) return null;
            return switch (databaseType) {
                case "ORACLE", "DAMENG" -> value.toUpperCase(Locale.ROOT);
                default -> value.toLowerCase(Locale.ROOT);
            };
        }

        private boolean isOracleDual(RawTable raw) {
            return databaseType.equals("ORACLE") && fold(raw.identifier().table()).equals("DUAL");
        }
    }

    private static SqlLineageAnalysis.Output copyOutput(SqlLineageAnalysis.Output output, String name) {
        return new SqlLineageAnalysis.Output(
                output.ordinal(), unquote(name), output.kind(), output.reliable(), output.expressionFingerprint(), output.sources());
    }

    private static String aliasOf(FromItem item, String fallback) {
        return item.getAlias() == null ? unquote(fallback) : unquote(item.getAlias().getUnquotedName());
    }

    private static boolean quoted(String value) {
        if (value == null || value.length() < 2) return false;
        return (value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("`") && value.endsWith("`"))
                || (value.startsWith("[") && value.endsWith("]"));
    }

    private static String unquote(String value) {
        if (!quoted(value)) return value;
        return value.substring(1, value.length() - 1);
    }

    private static boolean isNullExpression(Expression expression) {
        if (expression instanceof NullValue) return true;
        return expression instanceof CastExpression cast && isNullExpression(cast.getLeftExpression());
    }

    private static boolean isLiteral(Expression expression) {
        if (expression instanceof SignedExpression signed) return isLiteral(signed.getExpression());
        if (expression instanceof CastExpression cast) return isLiteral(cast.getLeftExpression());
        return expression instanceof StringValue || expression instanceof LongValue || expression instanceof DoubleValue
                || expression instanceof DateValue || expression instanceof TimeValue || expression instanceof TimestampValue
                || expression instanceof BooleanValue || expression instanceof HexValue || expression instanceof IntervalExpression;
    }

    private static String fingerprint(Expression expression) {
        String normalized = expression == null ? "unknown" : expression.toString().replaceAll("\\s+", " ").trim();
        normalized = STRING_LITERAL.matcher(normalized).replaceAll("?");
        normalized = NUMBER_LITERAL.matcher(normalized).replaceAll("?");
        return sha256(normalized.toLowerCase(Locale.ROOT));
    }

    private static String sha256(Object value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
