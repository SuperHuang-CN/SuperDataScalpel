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
import cn.superhuang.data.scalpel.contract.task.FileDatasetInputTableSelection;
import cn.superhuang.data.scalpel.contract.task.FileDatasetInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.FileDatasetParseStatus;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import cn.superhuang.datascalpel.taskengine.spark.SparkTypeMapper;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.util.List;
import java.util.LinkedHashMap;
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
            FileDatasetType.GEOJSON,
            FileDatasetType.GEOJSONL,
            FileDatasetType.GEOPARQUET,
            FileDatasetType.GPKG,
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
        UUID fileDatasetId = CanvasNodeSupport.parseUuid(
                configuration.fileDatasetId(), "configuration.fileDatasetId", issues);
        if (configuration.tables().isEmpty()) {
            issues.error("FILE_DATASET_TABLE_ID_REQUIRED", "至少选择一个文件数据集逻辑表", "configuration.tables");
        }
        List<ResolvedTable> resolved = new java.util.ArrayList<>();
        Set<UUID> tableIds = new java.util.HashSet<>();
        Set<String> outputNames = new java.util.HashSet<>();
        for (int index = 0; index < configuration.tables().size(); index++) {
            FileDatasetInputTableSelection selection = configuration.tables().get(index);
            String path = "configuration.tables[" + index + "]";
            if (selection == null) {
                issues.error("FILE_DATASET_TABLE_ID_REQUIRED", "文件数据集表不能为空", path);
                continue;
            }
            UUID tableId = CanvasNodeSupport.parseUuid(selection.fileDatasetTableId(), path + ".fileDatasetTableId", issues);
            if (tableId == null) continue;
            if (!tableIds.add(tableId)) {
                issues.error("DUPLICATE_FILE_DATASET_TABLE_SELECTION", "文件数据集表重复", path + ".fileDatasetTableId");
                continue;
            }
            MetadataIndex.FileDatasetTableEntry table = context.metadataIndex().fileDatasetTable(tableId);
            if (table == null) {
                issues.error("FILE_DATASET_TABLE_NOT_FOUND", "文件数据集表不存在", path + ".fileDatasetTableId");
                continue;
            }
            if (fileDatasetId != null && !fileDatasetId.equals(table.metadata().fileDatasetId())) {
                issues.error("FILE_DATASET_TABLE_SOURCE_MISMATCH", "文件数据集表不属于当前文件数据集", path + ".fileDatasetTableId");
            }
            validateTable(table, path, issues);
            if (!outputNames.add(table.tableSchema().name())) {
                issues.error("DUPLICATE_TABLE_NAME", "文件数据集输出表名重复：" + table.tableSchema().name(), path);
            }
            resolved.add(new ResolvedTable(selection, table));
        }
        if (issues.hasErrors()) return CanvasNodeOperationResult.invalid(List.of());
        Map<String, SparkCanvasTable> output = new LinkedHashMap<>();
        List<CanvasTableSchema> schemas = new java.util.ArrayList<>();
        for (ResolvedTable item : resolved) {
            CanvasTableSchema schema = item.table().tableSchema();
            Dataset<Row> dataset = context.dataAccess().readFileDatasetInput(node, item.selection(), item.table(), schema);
            output.put(schema.name(), new SparkCanvasTable(schema, dataset));
            schemas.add(schema);
        }
        return CanvasNodeOperationResult.propagated(output, schemas);
    }

    private static void validateTable(
            MetadataIndex.FileDatasetTableEntry table, String path, CanvasNodeIssueSink issues
    ) {
        if (table.metadata().fileStatus() != FileDatasetFileStatus.READY) {
            issues.error("FILE_DATASET_FILE_NOT_READY", "文件数据集来源文件尚未就绪", path);
        }
        if (table.metadata().parseStatus() != FileDatasetParseStatus.READY
                && table.metadata().parseStatus() != FileDatasetParseStatus.SCHEMA_READY) {
            issues.error("FILE_DATASET_TABLE_NOT_READY", "文件数据集表尚未完成 Schema 解析", path);
        }
        if (!SUPPORTED_DATASET_TYPES.contains(table.metadata().datasetType())) {
            issues.error("FILE_DATASET_FORMAT_NOT_SUPPORTED", "Task Engine 不支持该文件数据集格式", path);
        }
        if (table.metadata().columns().isEmpty()) {
            issues.error("FILE_DATASET_SCHEMA_EMPTY", "文件数据集表 Schema 为空", path);
        }
        try {
            SparkTypeMapper.toStructType(table.tableSchema().columns());
        } catch (IllegalArgumentException exception) {
            issues.error("FILE_DATASET_SCHEMA_UNSUPPORTED", "文件数据集 Schema 无法转换为 Spark Schema", path);
        }
    }

    private record ResolvedTable(
            FileDatasetInputTableSelection selection, MetadataIndex.FileDatasetTableEntry table
    ) {
    }
}
