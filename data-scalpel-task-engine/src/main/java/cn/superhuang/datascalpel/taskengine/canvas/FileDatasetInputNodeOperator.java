package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.datascalpel.taskengine.compiler.MetadataIndex;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.datascalpel.taskengine.contract.CanvasNodeCategory;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.FileDatasetFileStatus;
import cn.superhuang.data.scalpel.contract.task.FileDatasetType;
import cn.superhuang.data.scalpel.contract.task.FileDatasetInputConfiguration;
import cn.superhuang.data.scalpel.contract.task.FileDatasetInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.FileDatasetParseStatus;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import cn.superhuang.datascalpel.taskengine.spark.SparkTypeMapper;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class FileDatasetInputNodeOperator implements CanvasNodeOperator {

    private static final Set<FileDatasetType> SUPPORTED_DATASET_TYPES = Set.of(
            FileDatasetType.CSV,
            FileDatasetType.TSV,
            FileDatasetType.TXT,
            FileDatasetType.JSON,
            FileDatasetType.JSONL,
            FileDatasetType.PARQUET,
            FileDatasetType.AVRO,
            FileDatasetType.EXCEL,
            FileDatasetType.GDB,
            FileDatasetType.SHP
    );

    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.FILE_DATASET_INPUT;
    }

    @Override
    public CanvasNodeCategory category() {
        return CanvasNodeCategory.INPUT;
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
        if (!(definition instanceof FileDatasetInputNodeDefinition node)) {
            throw new IllegalArgumentException("FILE_DATASET_INPUT operator received " + definition.nodeType());
        }
        FileDatasetInputConfiguration configuration = node.configuration();
        if (configuration == null) {
            return CanvasNodeOperationResult.invalid(List.of());
        }
        CanvasNodeIssueSink issues = context.issues();
        if (configuration.fileDatasetTableId() == null
                || configuration.fileDatasetTableId().isBlank()) {
            issues.error(
                    "FILE_DATASET_TABLE_ID_REQUIRED",
                    "文件数据集表 ID 不能为空",
                    "configuration.fileDatasetTableId"
            );
            return CanvasNodeOperationResult.invalid(List.of());
        }
        UUID tableId;
        try {
            tableId = UUID.fromString(configuration.fileDatasetTableId());
        } catch (IllegalArgumentException exception) {
            issues.error(
                    "FILE_DATASET_TABLE_ID_REQUIRED",
                    "文件数据集表 ID 必须是合法 UUID",
                    "configuration.fileDatasetTableId"
            );
            return CanvasNodeOperationResult.invalid(List.of());
        }
        MetadataIndex.FileDatasetTableEntry table = context.metadataIndex().fileDatasetTable(tableId);
        if (table == null) {
            issues.error(
                    "FILE_DATASET_TABLE_NOT_FOUND",
                    "文件数据集表不存在",
                    "configuration.fileDatasetTableId"
            );
            return CanvasNodeOperationResult.invalid(List.of());
        }
        if (table.metadata().fileStatus() != FileDatasetFileStatus.READY) {
            issues.error(
                    "FILE_DATASET_FILE_NOT_READY",
                    "文件数据集来源文件尚未就绪",
                    "configuration.fileDatasetTableId"
            );
            return CanvasNodeOperationResult.invalid(List.of());
        }
        if (table.metadata().parseStatus() != FileDatasetParseStatus.READY
                && table.metadata().parseStatus() != FileDatasetParseStatus.SCHEMA_READY) {
            issues.error(
                    "FILE_DATASET_TABLE_NOT_READY",
                    "文件数据集表尚未完成 Schema 解析",
                    "configuration.fileDatasetTableId"
            );
            return CanvasNodeOperationResult.invalid(List.of());
        }
        if (!SUPPORTED_DATASET_TYPES.contains(table.metadata().datasetType())) {
            issues.error(
                    "FILE_DATASET_FORMAT_NOT_SUPPORTED",
                    "Task Engine 不支持该文件数据集格式",
                    "configuration.fileDatasetTableId"
            );
            return CanvasNodeOperationResult.invalid(List.of());
        }
        if (table.metadata().columns().isEmpty()) {
            issues.error(
                    "FILE_DATASET_SCHEMA_EMPTY",
                    "文件数据集表 Schema 为空",
                    "configuration.fileDatasetTableId"
            );
            return CanvasNodeOperationResult.invalid(List.of());
        }
        CanvasTableSchema schema = table.tableSchema();
        try {
            SparkTypeMapper.toStructType(schema.columns());
        } catch (IllegalArgumentException exception) {
            issues.error(
                    "FILE_DATASET_SCHEMA_UNSUPPORTED",
                    "文件数据集 Schema 无法转换为 Spark Schema",
                    "configuration.fileDatasetTableId"
            );
            return CanvasNodeOperationResult.invalid(List.of());
        }
        Dataset<Row> dataset = context.dataAccess().readFileDatasetInput(node, table, schema);
        return CanvasNodeOperationResult.propagated(
                Map.of(schema.name(), new SparkCanvasTable(schema, dataset)),
                List.of(schema)
        );
    }
}
