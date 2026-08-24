package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.BinaryExpression;
import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasExpression;
import cn.superhuang.data.scalpel.contract.task.CanvasExpressionLimits;
import cn.superhuang.data.scalpel.contract.task.CanvasLiteral;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.CaseWhenBranch;
import cn.superhuang.data.scalpel.contract.task.CaseWhenExpression;
import cn.superhuang.data.scalpel.contract.task.ColumnDerivation;
import cn.superhuang.data.scalpel.contract.task.ColumnExpression;
import cn.superhuang.data.scalpel.contract.task.DeriveColumnsConfiguration;
import cn.superhuang.data.scalpel.contract.task.DeriveColumnsOperation;
import cn.superhuang.data.scalpel.contract.task.DeriveColumnsNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.DeriveFunction;
import cn.superhuang.data.scalpel.contract.task.FunctionExpression;
import cn.superhuang.data.scalpel.contract.task.LiteralExpression;
import cn.superhuang.data.scalpel.contract.task.ProcessorOutput;
import cn.superhuang.data.scalpel.contract.task.RuntimeValueExpression;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.contract.CanvasNodeCategory;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import cn.superhuang.datascalpel.taskengine.spark.SparkTypeMapper;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.functions;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DeriveColumnsNodeOperator implements CanvasNodeOperator {

    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.DERIVE_COLUMNS;
    }

    @Override
    public CanvasNodeCategory category() {
        return CanvasNodeCategory.PROCESSOR;
    }

    @Override
    public Set<CanvasExecutionMode> supportedModes() {
        return Set.of(CanvasExecutionMode.BATCH, CanvasExecutionMode.STREAMING);
    }

    @Override
    public CanvasNodeOperationResult apply(
            CanvasNodeDefinition definition,
            Map<String, SparkCanvasTable> inputs,
            CanvasNodeOperationContext context
    ) {
        if (!(definition instanceof DeriveColumnsNodeDefinition node)) {
            throw new IllegalArgumentException(
                    "DERIVE_COLUMNS operator received " + definition.nodeType()
            );
        }
        List<CanvasTableSchema> inputSchemas = CanvasNodeSupport.schemas(inputs);
        DeriveColumnsConfiguration configuration = node.configuration();
        if (configuration == null) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }
        if (!ProcessorOperationSupport.isInternalSingle(configuration.operations())) {
            validateGlobalDerivations(configuration, inputs, context);
            if (context.issues().hasErrors()) {
                return CanvasNodeOperationResult.invalid(inputSchemas);
            }
            return ProcessorOperationSupport.apply(configuration.operations(), inputs, context, false,
                    (operation, scopedContext) -> {
                        DeriveColumnsOperation sourceOperation = (DeriveColumnsOperation) operation.operation();
                        List<ColumnDerivation> effectiveDerivations = new ArrayList<>(configuration.globalDerivations());
                        effectiveDerivations.addAll(sourceOperation.derivations() == null
                                ? List.of() : sourceOperation.derivations());
                        DeriveColumnsConfiguration single = new DeriveColumnsConfiguration(List.of(new DeriveColumnsOperation(
                                ProcessorOperationSupport.INTERNAL_OPERATION_ID, operation.temporarySourceTableName(),
                                new ProcessorOutput.CreateNewTable(operation.outputTableName()), effectiveDerivations
                        )));
                        return apply(new DeriveColumnsNodeDefinition(node.id(), node.name(), node.layout(), single),
                                Map.of(operation.temporarySourceTableName(), operation.source()), scopedContext);
                    });
        }

        CanvasNodeIssueSink issues = context.issues();
        CanvasNodeSupport.required(
                configuration.sourceTableName(),
                "请选择来源表",
                "configuration.sourceTableName",
                issues
        );
        CanvasNodeSupport.required(
                configuration.outputTableName(),
                "请输入输出表名",
                "configuration.outputTableName",
                issues
        );
        if (!CanvasNodeSupport.blank(configuration.outputTableName())
                && inputs.containsKey(configuration.outputTableName())) {
            issues.error(
                    "DUPLICATE_TABLE_NAME",
                    "输出表名已存在：" + configuration.outputTableName(),
                    "configuration.outputTableName"
            );
        }

        SparkCanvasTable source = CanvasNodeSupport.blank(configuration.sourceTableName())
                ? null
                : inputs.get(configuration.sourceTableName());
        if (!CanvasNodeSupport.blank(configuration.sourceTableName()) && source == null) {
            issues.error(
                    "TABLE_NOT_FOUND",
                    "来源表不在上游数据中：" + configuration.sourceTableName(),
                    "configuration.sourceTableName"
            );
        }
        if (configuration.derivations() == null) {
            issues.error(
                    "REQUIRED_CONFIGURATION",
                    "派生字段必须是数组",
                    "configuration.derivations"
            );
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }
        if (configuration.derivations().isEmpty()) {
            issues.error(
                    "EMPTY_DERIVATIONS",
                    "至少配置一个派生字段",
                    "configuration.derivations"
            );
        }
        if (configuration.derivations().size() > CanvasExpressionLimits.MAX_DERIVATIONS) {
            issues.error(
                    "INVALID_DERIVATION_EXPRESSION",
                    "派生字段不能超过 " + CanvasExpressionLimits.MAX_DERIVATIONS + " 项",
                    "configuration.derivations"
            );
        }

        Map<String, CanvasColumnSchema> sourceColumns = source == null
                ? Map.of()
                : CanvasNodeSupport.columns(source.schema());
        Set<String> targets = new HashSet<>();
        int[] expressionNodes = {0};
        for (int index = 0; index < configuration.derivations().size(); index++) {
            ColumnDerivation derivation = configuration.derivations().get(index);
            String path = "configuration.derivations[" + index + "]";
            if (derivation == null) {
                issues.error("REQUIRED_CONFIGURATION", "派生字段配置不能为空", path);
                continue;
            }
            CanvasNodeSupport.required(
                    derivation.targetColumnName(),
                    "请输入目标字段名",
                    path + ".targetColumnName",
                    issues
            );
            if (!CanvasNodeSupport.blank(derivation.targetColumnName())
                    && !targets.add(derivation.targetColumnName())) {
                issues.error(
                        "DUPLICATE_DERIVATION_TARGET",
                        "目标字段重复配置：" + derivation.targetColumnName(),
                        path + ".targetColumnName"
                );
            }
            if (!CanvasNodeSupport.blank(derivation.targetColumnName())) {
                boolean exists = sourceColumns.containsKey(derivation.targetColumnName());
                if (context.executionMode() == CanvasExecutionMode.STREAMING
                        && exists
                        && source != null
                        && derivation.targetColumnName().equals(
                                source.schema().eventTimeColumn())) {
                    issues.error(
                            "STREAM_EVENT_TIME_COLUMN_IMMUTABLE",
                            "流任务不能覆盖事件时间字段：" + derivation.targetColumnName(),
                            path + ".targetColumnName"
                    );
                }
            }
            if (derivation.expression() == null) {
                issues.error(
                        "INVALID_DERIVATION_EXPRESSION",
                        "请配置派生表达式",
                        path + ".expression"
                );
                continue;
            }
            validateExpression(
                    derivation.expression(),
                    source,
                    issues,
                    path + ".expression",
                    1,
                    expressionNodes
            );
        }
        if (source == null || issues.hasErrors()) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }

        Map<String, ColumnDerivation> derivationsByTarget = new LinkedHashMap<>();
        configuration.derivations().forEach(
                derivation -> derivationsByTarget.put(derivation.targetColumnName(), derivation)
        );
        Dataset<Row> sourceDataset = source.dataset();
        List<Column> projection = new ArrayList<>(
                source.schema().columns().size() + configuration.derivations().size()
        );
        for (CanvasColumnSchema sourceColumn : source.schema().columns()) {
            ColumnDerivation replacement = derivationsByTarget.get(sourceColumn.name());
            projection.add(replacement == null
                    ? sourceDataset.col(CanvasNodeSupport.quoteIdentifier(sourceColumn.name()))
                    : expression(replacement.expression(), sourceDataset, context)
                            .alias(replacement.targetColumnName()));
        }
        for (ColumnDerivation derivation : configuration.derivations()) {
            if (!sourceColumns.containsKey(derivation.targetColumnName())) {
                projection.add(
                        expression(derivation.expression(), sourceDataset, context)
                                .alias(derivation.targetColumnName())
                );
            }
        }
        Dataset<Row> derivedDataset = sourceDataset.select(projection.toArray(Column[]::new));
        List<CanvasColumnSchema> analyzedColumns = SparkTypeMapper.fromStructType(
                derivedDataset.schema(),
                List.of()
        );
        List<CanvasColumnSchema> outputColumns = new ArrayList<>(analyzedColumns.size());
        for (int index = 0; index < analyzedColumns.size(); index++) {
            CanvasColumnSchema analyzed = analyzedColumns.get(index);
            CanvasColumnSchema original = sourceColumns.get(analyzed.name());
            boolean unchanged = original != null
                    && !derivationsByTarget.containsKey(analyzed.name());
            outputColumns.add(unchanged ? original : derivedColumn(analyzed));
        }

        CanvasTableSchema outputSchema = new CanvasTableSchema(
                configuration.outputTableName(),
                source.schema().origin(),
                outputColumns,
                source.schema().datasetKind(),
                source.schema().eventTimeColumn(),
                source.schema().watermarkDelay()
        );
        Map<String, SparkCanvasTable> output = new LinkedHashMap<>(inputs);
        output.put(
                outputSchema.name(),
                new SparkCanvasTable(outputSchema, derivedDataset)
        );
        return CanvasNodeOperationResult.propagated(output, CanvasNodeSupport.schemas(output));
    }

    private static void validateGlobalDerivations(
            DeriveColumnsConfiguration configuration,
            Map<String, SparkCanvasTable> inputs,
            CanvasNodeOperationContext context
    ) {
        List<ColumnDerivation> globalDerivations = configuration.globalDerivations();
        if (globalDerivations.isEmpty()) {
            return;
        }
        CanvasNodeIssueSink issues = context.issues();
        Set<String> globalTargets = new HashSet<>();
        for (int globalIndex = 0; globalIndex < globalDerivations.size(); globalIndex++) {
            ColumnDerivation derivation = globalDerivations.get(globalIndex);
            String path = "configuration.globalDerivations[" + globalIndex + "]";
            if (derivation == null) {
                issues.error("REQUIRED_CONFIGURATION", "全局派生字段配置不能为空", path);
                continue;
            }
            CanvasNodeSupport.required(derivation.targetColumnName(), "请输入目标字段名", path + ".targetColumnName", issues);
            if (!CanvasNodeSupport.blank(derivation.targetColumnName())
                    && !globalTargets.add(derivation.targetColumnName())) {
                issues.error("DUPLICATE_DERIVATION_TARGET", "全局目标字段重复配置：" + derivation.targetColumnName(),
                        path + ".targetColumnName");
            }
            if (derivation.expression() == null) {
                issues.error("INVALID_DERIVATION_EXPRESSION", "请配置派生表达式", path + ".expression");
            }
        }
        if (configuration.operations() == null) {
            return;
        }
        for (int operationIndex = 0; operationIndex < configuration.operations().size(); operationIndex++) {
            DeriveColumnsOperation operation = configuration.operations().get(operationIndex);
            if (operation == null || CanvasNodeSupport.blank(operation.sourceTableName())) {
                continue;
            }
            SparkCanvasTable source = inputs.get(operation.sourceTableName());
            if (source == null) {
                continue;
            }
            Map<String, CanvasColumnSchema> sourceColumns = CanvasNodeSupport.columns(source.schema());
            Set<String> localTargets = new HashSet<>();
            List<ColumnDerivation> localDerivations = operation.derivations() == null ? List.of() : operation.derivations();
            for (int localIndex = 0; localIndex < localDerivations.size(); localIndex++) {
                ColumnDerivation derivation = localDerivations.get(localIndex);
                if (derivation != null && !CanvasNodeSupport.blank(derivation.targetColumnName())) {
                    localTargets.add(derivation.targetColumnName());
                }
            }
            int[] expressionNodes = {0};
            for (int globalIndex = 0; globalIndex < globalDerivations.size(); globalIndex++) {
                ColumnDerivation derivation = globalDerivations.get(globalIndex);
                if (derivation == null) {
                    continue;
                }
                String path = "configuration.globalDerivations[" + globalIndex + "]";
                if (!CanvasNodeSupport.blank(derivation.targetColumnName())) {
                    if (localTargets.contains(derivation.targetColumnName())) {
                        issues.error("GLOBAL_DERIVATION_TARGET_CONFLICT",
                                "全局规则与表 " + operation.sourceTableName() + " 的独立规则使用了同一目标字段："
                                        + derivation.targetColumnName(),
                                path + ".targetColumnName");
                    }
                    boolean exists = sourceColumns.containsKey(derivation.targetColumnName());
                    if (context.executionMode() == CanvasExecutionMode.STREAMING
                            && exists
                            && derivation.targetColumnName().equals(source.schema().eventTimeColumn())) {
                        issues.error("STREAM_EVENT_TIME_COLUMN_IMMUTABLE",
                                "全局规则不能覆盖表 " + operation.sourceTableName() + " 的事件时间字段："
                                        + derivation.targetColumnName(),
                                path + ".targetColumnName");
                    }
                }
                if (derivation.expression() != null) {
                    validateExpression(derivation.expression(), source, issues, path + ".expression", 1, expressionNodes);
                }
            }
        }
    }

    private static void validateExpression(
            CanvasExpression expression,
            SparkCanvasTable source,
            CanvasNodeIssueSink issues,
            String path,
            int depth,
            int[] expressionNodes
    ) {
        if (depth > CanvasExpressionLimits.MAX_DEPTH) {
            issues.error(
                    "INVALID_DERIVATION_EXPRESSION",
                    "表达式超过最大嵌套深度 " + CanvasExpressionLimits.MAX_DEPTH,
                    path
            );
            return;
        }
        expressionNodes[0]++;
        if (expressionNodes[0] > CanvasExpressionLimits.MAX_EXPRESSION_NODES) {
            issues.error(
                    "INVALID_DERIVATION_EXPRESSION",
                    "表达式节点不能超过 " + CanvasExpressionLimits.MAX_EXPRESSION_NODES,
                    path
            );
            return;
        }
        switch (expression) {
            case ColumnExpression column -> {
                CanvasNodeSupport.required(
                        column.columnName(),
                        "请选择引用字段",
                        path + ".columnName",
                        issues
                );
                if (source != null
                        && !CanvasNodeSupport.blank(column.columnName())
                        && !CanvasNodeSupport.columns(source.schema())
                                .containsKey(column.columnName())) {
                    issues.error(
                            "COLUMN_NOT_FOUND",
                            "来源字段不存在：" + column.columnName(),
                            path + ".columnName"
                    );
                }
            }
            case LiteralExpression literal -> {
                if (!CanvasPredicateExpressionBuilder.validLiteral(literal.literal())) {
                    issues.error(
                            "INVALID_DERIVATION_EXPRESSION",
                            "Literal 格式无效",
                            path + ".literal"
                    );
                }
            }
            case RuntimeValueExpression runtime -> {
                if (runtime.value() == null) {
                    issues.error(
                            "INVALID_RUNTIME_VALUE",
                            "请选择受支持的运行时变量",
                            path + ".value"
                    );
                }
            }
            case BinaryExpression binary -> {
                if (binary.operator() == null || binary.left() == null || binary.right() == null) {
                    issues.error(
                            "INVALID_DERIVATION_EXPRESSION",
                            "二元表达式结构不完整",
                            path
                    );
                    return;
                }
                validateExpression(
                        binary.left(), source, issues, path + ".left",
                        depth + 1, expressionNodes
                );
                validateExpression(
                        binary.right(), source, issues, path + ".right",
                        depth + 1, expressionNodes
                );
            }
            case FunctionExpression function ->
                    validateFunction(
                            function, source, issues, path, depth, expressionNodes
                    );
            case CaseWhenExpression caseWhen ->
                    validateCaseWhen(
                            caseWhen, source, issues, path, depth, expressionNodes
                    );
        }
    }

    private static void validateFunction(
            FunctionExpression function,
            SparkCanvasTable source,
            CanvasNodeIssueSink issues,
            String path,
            int depth,
            int[] expressionNodes
    ) {
        if (function.function() == null) {
            issues.error(
                    "UNSUPPORTED_EXPRESSION_FUNCTION",
                    "请选择受支持的函数",
                    path + ".function"
            );
            return;
        }
        if (function.arguments() == null) {
            issues.error(
                    "INVALID_FUNCTION_ARGUMENTS",
                    "函数参数必须是数组",
                    path + ".arguments"
            );
            return;
        }
        if (!validArgumentCount(function.function(), function.arguments().size())) {
            issues.error(
                    "INVALID_FUNCTION_ARGUMENTS",
                    argumentCountMessage(function.function()),
                    path + ".arguments"
            );
        }
        if (function.function() == DeriveFunction.DATE_FORMAT
                && !isStringLiteral(argumentAt(function, 1))) {
            issues.error(
                    "INVALID_FUNCTION_ARGUMENTS",
                    "DATE_FORMAT 第二个参数必须是 STRING Literal",
                    path + ".arguments[1]"
            );
        }
        if ((function.function() == DeriveFunction.DATE_ADD
                || function.function() == DeriveFunction.DATE_SUB)
                && !isIntegralLiteral(argumentAt(function, 1))) {
            issues.error(
                    "INVALID_FUNCTION_ARGUMENTS",
                    function.function() + " 第二个参数必须是整数 Literal",
                    path + ".arguments[1]"
            );
        }
        for (int index = 0; index < function.arguments().size(); index++) {
            CanvasExpression argument = function.arguments().get(index);
            if (argument == null) {
                issues.error(
                        "INVALID_DERIVATION_EXPRESSION",
                        "函数参数不能为空",
                        path + ".arguments[" + index + "]"
                );
                continue;
            }
            validateExpression(
                    argument,
                    source,
                    issues,
                    path + ".arguments[" + index + "]",
                    depth + 1,
                    expressionNodes
            );
        }
    }

    private static void validateCaseWhen(
            CaseWhenExpression caseWhen,
            SparkCanvasTable source,
            CanvasNodeIssueSink issues,
            String path,
            int depth,
            int[] expressionNodes
    ) {
        if (caseWhen.branches() == null || caseWhen.branches().isEmpty()) {
            issues.error(
                    "INVALID_DERIVATION_EXPRESSION",
                    "CASE_WHEN 至少需要一个分支",
                    path + ".branches"
            );
            return;
        }
        if (caseWhen.branches().size() > CanvasExpressionLimits.MAX_CASE_BRANCHES) {
            issues.error(
                    "INVALID_DERIVATION_EXPRESSION",
                    "CASE_WHEN 分支不能超过 " + CanvasExpressionLimits.MAX_CASE_BRANCHES,
                    path + ".branches"
            );
        }
        for (int index = 0; index < caseWhen.branches().size(); index++) {
            CaseWhenBranch branch = caseWhen.branches().get(index);
            String branchPath = path + ".branches[" + index + "]";
            if (branch == null || branch.condition() == null || branch.result() == null) {
                issues.error(
                        "INVALID_DERIVATION_EXPRESSION",
                        "CASE_WHEN 分支结构不完整",
                        branchPath
                );
                continue;
            }
            CanvasPredicateExpressionBuilder.validate(
                    branch.condition(),
                    source,
                    issues,
                    branchPath + ".condition"
            );
            validateExpression(
                    branch.result(),
                    source,
                    issues,
                    branchPath + ".result",
                    depth + 1,
                    expressionNodes
            );
        }
        if (caseWhen.elseExpression() != null) {
            validateExpression(
                    caseWhen.elseExpression(),
                    source,
                    issues,
                    path + ".elseExpression",
                    depth + 1,
                    expressionNodes
            );
        }
    }

    private static boolean validArgumentCount(DeriveFunction function, int count) {
        return switch (function) {
            case TRIM, LTRIM, RTRIM, LOWER, UPPER -> count == 1;
            case REPLACE, SUBSTRING -> count == 3;
            case COALESCE, CONCAT -> count >= 2;
            case DATE_FORMAT, DATE_ADD, DATE_SUB -> count == 2;
        };
    }

    private static String argumentCountMessage(DeriveFunction function) {
        return switch (function) {
            case TRIM, LTRIM, RTRIM, LOWER, UPPER ->
                    function + " 必须且只能有一个参数";
            case REPLACE, SUBSTRING ->
                    function + " 必须且只能有三个参数";
            case COALESCE, CONCAT ->
                    function + " 至少需要两个参数";
            case DATE_FORMAT, DATE_ADD, DATE_SUB ->
                    function + " 必须且只能有两个参数";
        };
    }

    private static CanvasExpression argumentAt(FunctionExpression function, int index) {
        return function.arguments() != null && function.arguments().size() > index
                ? function.arguments().get(index)
                : null;
    }

    private static boolean isStringLiteral(CanvasExpression expression) {
        return expression instanceof LiteralExpression literal
                && literal.literal() != null
                && literal.literal().dataType() == PlatformDataType.STRING
                && CanvasPredicateExpressionBuilder.validLiteral(literal.literal());
    }

    private static boolean isIntegralLiteral(CanvasExpression expression) {
        if (!(expression instanceof LiteralExpression literal)
                || literal.literal() == null
                || !CanvasPredicateExpressionBuilder.validLiteral(literal.literal())) {
            return false;
        }
        return switch (literal.literal().dataType()) {
            case BYTE, SHORT, INTEGER, LONG -> true;
            default -> false;
        };
    }

    private static Column expression(
            CanvasExpression expression,
            Dataset<Row> source,
            CanvasNodeOperationContext context
    ) {
        return switch (expression) {
            case ColumnExpression column ->
                    source.col(CanvasNodeSupport.quoteIdentifier(column.columnName()));
            case LiteralExpression literal ->
                    functions.lit(
                            CanvasPredicateExpressionBuilder.literalValue(literal.literal())
                    );
            case RuntimeValueExpression runtime -> runtimeValueExpression(runtime, context);
            case BinaryExpression binary -> {
                Column left = expression(binary.left(), source, context);
                Column right = expression(binary.right(), source, context);
                yield switch (binary.operator()) {
                    case ADD -> left.plus(right);
                    case SUBTRACT -> left.minus(right);
                    case MULTIPLY -> left.multiply(right);
                    case DIVIDE -> left.divide(right);
                    case MODULO -> left.mod(right);
                };
            }
            case FunctionExpression function -> functionExpression(function, source, context);
            case CaseWhenExpression caseWhen -> caseWhenExpression(caseWhen, source, context);
        };
    }

    private static Column runtimeValueExpression(
            RuntimeValueExpression runtime,
            CanvasNodeOperationContext context
    ) {
        return switch (runtime.value()) {
            case EXECUTION_ID -> functions.lit(context.runtimeValues().executionId().toString());
            case EXECUTION_STARTED_AT -> functions.lit(
                    Timestamp.from(context.runtimeValues().executionStartedAt())
            );
        };
    }

    private static Column functionExpression(
            FunctionExpression function,
            Dataset<Row> source,
            CanvasNodeOperationContext context
    ) {
        Column[] arguments = function.arguments().stream()
                .map(argument -> expression(argument, source, context))
                .toArray(Column[]::new);
        return switch (function.function()) {
            case TRIM -> functions.trim(arguments[0]);
            case LTRIM -> functions.ltrim(arguments[0]);
            case RTRIM -> functions.rtrim(arguments[0]);
            case LOWER -> functions.lower(arguments[0]);
            case UPPER -> functions.upper(arguments[0]);
            case REPLACE -> functions.replace(arguments[0], arguments[1], arguments[2]);
            case SUBSTRING ->
                    functions.substring(arguments[0], arguments[1], arguments[2]);
            case COALESCE -> functions.coalesce(arguments);
            case CONCAT -> functions.concat(arguments);
            case DATE_FORMAT -> functions.date_format(
                    arguments[0],
                    ((LiteralExpression) function.arguments().get(1)).literal().value()
            );
            case DATE_ADD -> functions.date_add(arguments[0], arguments[1]);
            case DATE_SUB -> functions.date_sub(arguments[0], arguments[1]);
        };
    }

    private static Column caseWhenExpression(
            CaseWhenExpression caseWhen,
            Dataset<Row> source,
            CanvasNodeOperationContext context
    ) {
        CaseWhenBranch first = caseWhen.branches().getFirst();
        Column result = functions.when(
                CanvasPredicateExpressionBuilder.expression(first.condition(), source),
                expression(first.result(), source, context)
        );
        for (int index = 1; index < caseWhen.branches().size(); index++) {
            CaseWhenBranch branch = caseWhen.branches().get(index);
            result = result.when(
                    CanvasPredicateExpressionBuilder.expression(branch.condition(), source),
                    expression(branch.result(), source, context)
            );
        }
        return caseWhen.elseExpression() == null
                ? result
                : result.otherwise(expression(caseWhen.elseExpression(), source, context));
    }

    private static CanvasColumnSchema derivedColumn(CanvasColumnSchema analyzed) {
        return new CanvasColumnSchema(
                analyzed.name(),
                analyzed.fieldType(),
                null,
                analyzed.fieldType() == PlatformDataType.DECIMAL
                        ? analyzed.precision()
                        : null,
                analyzed.fieldType() == PlatformDataType.DECIMAL
                        ? analyzed.scale()
                        : null,
                analyzed.nullable(),
                null,
                false,
                false,
                null
        );
    }
}
