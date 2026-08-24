package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasDatasetKind;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasTopNLimits;
import cn.superhuang.data.scalpel.contract.task.TopNConfiguration;
import cn.superhuang.data.scalpel.contract.task.TopNOperation;
import cn.superhuang.data.scalpel.contract.task.TopNNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.TopNTieStrategy;
import cn.superhuang.data.scalpel.contract.task.ProcessorOutput;
import cn.superhuang.datascalpel.taskengine.contract.CanvasNodeCategory;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.expressions.Window;
import org.apache.spark.sql.expressions.WindowSpec;
import org.apache.spark.sql.functions;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TopNNodeOperator implements CanvasNodeOperator {

    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.TOP_N;
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
        if (!(definition instanceof TopNNodeDefinition node)) {
            throw new IllegalArgumentException(
                    "TOP_N operator received " + definition.nodeType()
            );
        }
        List<CanvasTableSchema> inputSchemas = CanvasNodeSupport.schemas(inputs);
        TopNConfiguration configuration = node.configuration();
        if (configuration == null) return CanvasNodeOperationResult.invalid(inputSchemas);
        if (!ProcessorOperationSupport.isInternalSingle(configuration.operations())) {
            return ProcessorOperationSupport.apply(configuration.operations(), inputs, context, false,
                    (operation, scopedContext) -> {
                        TopNOperation sourceOperation = (TopNOperation) operation.operation();
                        TopNConfiguration single = new TopNConfiguration(List.of(new TopNOperation(
                                ProcessorOperationSupport.INTERNAL_OPERATION_ID, operation.temporarySourceTableName(),
                                new ProcessorOutput.CreateNewTable(operation.outputTableName()),
                                sourceOperation.partitionByColumns(), sourceOperation.orderBy(),
                                sourceOperation.limit(), sourceOperation.tieStrategy()
                        )));
                        return apply(new TopNNodeDefinition(node.id(), node.name(), node.layout(), single),
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
                ? null : inputs.get(configuration.sourceTableName());
        if (!CanvasNodeSupport.blank(configuration.sourceTableName()) && source == null) {
            issues.error(
                    "TABLE_NOT_FOUND",
                    "来源表不在上游数据中：" + configuration.sourceTableName(),
                    "configuration.sourceTableName"
            );
        }
        if (source != null && source.schema().datasetKind() != CanvasDatasetKind.BOUNDED) {
            issues.error(
                    "TOP_N_REQUIRES_BOUNDED_INPUT",
                    "Top N 只支持有界输入",
                    "configuration.sourceTableName"
            );
        }
        WindowNodeOperator.validatePartitionColumns(
                configuration.partitionByColumns(),
                source,
                "DUPLICATE_TOP_N_PARTITION_COLUMN",
                issues,
                "configuration.partitionByColumns"
        );
        CanvasSortSupport.validate(
                configuration.orderBy(),
                source,
                issues,
                "configuration.orderBy",
                "EMPTY_TOP_N_ORDER",
                "DUPLICATE_TOP_N_SORT_COLUMN"
        );
        if (configuration.limit() < CanvasTopNLimits.MIN_LIMIT
                || configuration.limit() > CanvasTopNLimits.MAX_LIMIT) {
            issues.error(
                    "INVALID_TOP_N_LIMIT",
                    "Top N 必须在 "
                            + CanvasTopNLimits.MIN_LIMIT + ".."
                            + CanvasTopNLimits.MAX_LIMIT,
                    "configuration.limit"
            );
        }
        if (configuration.tieStrategy() == null) {
            issues.error(
                    "INVALID_TOP_N_TIE_STRATEGY",
                    "请选择并列处理策略",
                    "configuration.tieStrategy"
            );
        }
        if (source == null || issues.hasErrors()) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }

        Dataset<Row> sourceDataset = source.dataset();
        Column[] order = CanvasSortSupport.expressions(sourceDataset, configuration.orderBy());
        Dataset<Row> result;
        if (configuration.partitionByColumns().isEmpty()
                && configuration.tieStrategy() == TopNTieStrategy.EXACT) {
            result = sourceDataset.orderBy(order).limit(configuration.limit());
        } else {
            Column[] partitions = configuration.partitionByColumns().stream()
                    .map(name -> sourceDataset.col(CanvasNodeSupport.quoteIdentifier(name)))
                    .toArray(Column[]::new);
            WindowSpec window = partitions.length == 0
                    ? Window.orderBy(order)
                    : Window.partitionBy(partitions).orderBy(order);
            String rankColumn = CanvasSortSupport.temporaryColumnName(
                    CanvasNodeSupport.columns(source.schema()).keySet(),
                    "__datascalpel_top_n_rank"
            );
            Column rank = configuration.tieStrategy() == TopNTieStrategy.WITH_TIES
                    ? functions.rank() : functions.row_number();
            Dataset<Row> ranked = sourceDataset.withColumn(rankColumn, rank.over(window));
            Column[] projection = source.schema().columns().stream()
                    .map(column -> ranked.col(
                            CanvasNodeSupport.quoteIdentifier(column.name())))
                    .toArray(Column[]::new);
            result = ranked
                    .filter(ranked.col(
                            CanvasNodeSupport.quoteIdentifier(rankColumn))
                            .leq(configuration.limit()))
                    .select(projection);
        }
        result.schema();

        CanvasTableSchema outputSchema = new CanvasTableSchema(
                configuration.outputTableName(),
                source.schema().origin(),
                source.schema().columns(),
                CanvasDatasetKind.BOUNDED,
                null,
                null
        );
        Map<String, SparkCanvasTable> output = new LinkedHashMap<>(inputs);
        output.put(outputSchema.name(), new SparkCanvasTable(outputSchema, result));
        return CanvasNodeOperationResult.propagated(output, CanvasNodeSupport.schemas(output));
    }
}
