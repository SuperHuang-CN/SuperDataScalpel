package cn.superhuang.datascalpel.taskengine.compiler;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.ConnectionKind;
import cn.superhuang.data.scalpel.contract.task.MetadataDataSource;
import cn.superhuang.data.scalpel.contract.task.MetadataFileDatasetTable;
import cn.superhuang.data.scalpel.contract.task.MetadataModel;
import cn.superhuang.data.scalpel.contract.task.MetadataSnapshot;
import cn.superhuang.data.scalpel.contract.task.MetadataTable;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableOrigin;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.datascalpel.taskengine.http.TaskEngineException;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class MetadataIndex {
    private final Map<UUID, DataSourceEntry> dataSources;
    private final Map<UUID, ModelEntry> models;
    private final Map<String, ModelEntry> modelsByCode;
    private final Map<UUID, FileDatasetTableEntry> fileDatasetTables;

    private MetadataIndex(
            Map<UUID, DataSourceEntry> dataSources,
            Map<UUID, ModelEntry> models,
            Map<String, ModelEntry> modelsByCode,
            Map<UUID, FileDatasetTableEntry> fileDatasetTables
    ) {
        this.dataSources = Map.copyOf(dataSources);
        this.models = Map.copyOf(models);
        this.modelsByCode = Map.copyOf(modelsByCode);
        this.fileDatasetTables = Map.copyOf(fileDatasetTables);
    }

    public static MetadataIndex create(MetadataSnapshot snapshot) {
        if (snapshot == null || snapshot.dataSources() == null) {
            throw invalid("metadataSnapshot.dataSources is required");
        }
        Map<UUID, DataSourceEntry> dataSources = new LinkedHashMap<>();
        for (int dataSourceIndex = 0; dataSourceIndex < snapshot.dataSources().size(); dataSourceIndex++) {
            MetadataDataSource dataSource = snapshot.dataSources().get(dataSourceIndex);
            String path = "metadataSnapshot.dataSources[" + dataSourceIndex + "]";
            if (dataSource == null || dataSource.id() == null) {
                throw invalid(path + ".id is required");
            }
            if (dataSource.connectionKind() == null) {
                throw invalid(path + ".connectionKind is required");
            }
            if (dataSource.purposes() == null) {
                throw invalid(path + ".purposes is required");
            }
            if (dataSource.purposes().stream().anyMatch(java.util.Objects::isNull)) {
                throw invalid(path + ".purposes must not contain null");
            }
            if (dataSource.tables() == null) {
                throw invalid(path + ".tables is required");
            }
            Map<String, MetadataTable> tables = new LinkedHashMap<>();
            for (int tableIndex = 0; tableIndex < dataSource.tables().size(); tableIndex++) {
                MetadataTable table = dataSource.tables().get(tableIndex);
                String tablePath = path + ".tables[" + tableIndex + "]";
                validateTable(table, tablePath);
                if (tables.putIfAbsent(table.tableName(), table) != null) {
                    throw invalid(tablePath + ".tableName duplicates table " + table.tableName());
                }
            }
            DataSourceEntry entry = new DataSourceEntry(dataSource, Map.copyOf(tables));
            if (dataSources.putIfAbsent(dataSource.id(), entry) != null) {
                throw invalid(path + ".id duplicates data source " + dataSource.id());
            }
        }
        if (snapshot.models() == null) {
            throw invalid("metadataSnapshot.models is required");
        }
        Map<UUID, ModelEntry> models = new LinkedHashMap<>();
        Map<String, ModelEntry> modelsByCode = new LinkedHashMap<>();
        for (int modelIndex = 0; modelIndex < snapshot.models().size(); modelIndex++) {
            MetadataModel model = snapshot.models().get(modelIndex);
            String path = "metadataSnapshot.models[" + modelIndex + "]";
            validateModel(model, path, dataSources);
            CanvasTableSchema tableSchema = new CanvasTableSchema(
                    model.code(),
                    CanvasTableOrigin.model(model.id(), model.code(), model.schemaVersion()),
                    model.columns()
            );
            ModelEntry entry = new ModelEntry(model, tableSchema);
            if (models.putIfAbsent(model.id(), entry) != null) {
                throw invalid(path + ".id duplicates model " + model.id());
            }
            if (modelsByCode.putIfAbsent(model.code(), entry) != null) {
                throw invalid(path + ".code duplicates model code " + model.code());
            }
        }
        Map<UUID, FileDatasetTableEntry> fileDatasetTables = new LinkedHashMap<>();
        for (int tableIndex = 0; tableIndex < snapshot.fileDatasetTables().size(); tableIndex++) {
            MetadataFileDatasetTable table = snapshot.fileDatasetTables().get(tableIndex);
            String path = "metadataSnapshot.fileDatasetTables[" + tableIndex + "]";
            validateFileDatasetTable(table, path);
            CanvasTableSchema tableSchema = new CanvasTableSchema(
                    table.code(),
                    CanvasTableOrigin.fileDataset(table.id()),
                    table.columns()
            );
            FileDatasetTableEntry entry = new FileDatasetTableEntry(table, tableSchema);
            if (fileDatasetTables.putIfAbsent(table.id(), entry) != null) {
                throw invalid(path + ".id duplicates file dataset table " + table.id());
            }
        }
        return new MetadataIndex(dataSources, models, modelsByCode, fileDatasetTables);
    }

    public DataSourceEntry dataSource(UUID id) {
        return dataSources.get(id);
    }

    public ModelEntry model(UUID id) {
        return models.get(id);
    }

    public ModelEntry modelByCode(String code) {
        return modelsByCode.get(code);
    }

    public FileDatasetTableEntry fileDatasetTable(UUID id) {
        return fileDatasetTables.get(id);
    }

    private static void validateFileDatasetTable(MetadataFileDatasetTable table, String path) {
        if (table == null || table.id() == null) {
            throw invalid(path + ".id is required");
        }
        if (blank(table.code())) {
            throw invalid(path + ".code is required");
        }
        if (blank(table.name())) {
            throw invalid(path + ".name is required");
        }
        if (table.datasetType() == null) {
            throw invalid(path + ".datasetType is required");
        }
        if (table.parseStatus() == null) {
            throw invalid(path + ".parseStatus is required");
        }
        if (table.fileStatus() == null) {
            throw invalid(path + ".fileStatus is required");
        }
        if (table.columns() == null) {
            throw invalid(path + ".columns is required");
        }
        Set<String> columnNames = new HashSet<>();
        for (int columnIndex = 0; columnIndex < table.columns().size(); columnIndex++) {
            CanvasColumnSchema column = table.columns().get(columnIndex);
            String columnPath = path + ".columns[" + columnIndex + "]";
            validateColumn(column, columnPath);
            if (!columnNames.add(column.name())) {
                throw invalid(columnPath + ".name duplicates column " + column.name());
            }
        }
    }

    private static void validateModel(
            MetadataModel model,
            String path,
            Map<UUID, DataSourceEntry> dataSources
    ) {
        if (model == null || model.id() == null) {
            throw invalid(path + ".id is required");
        }
        if (blank(model.code())) {
            throw invalid(path + ".code is required");
        }
        if (blank(model.name())) {
            throw invalid(path + ".name is required");
        }
        if (model.schemaVersion() < 1) {
            throw invalid(path + ".schemaVersion must be positive");
        }
        if (model.status() == null) {
            throw invalid(path + ".status is required");
        }
        if (model.physicalTableMode() == null) {
            throw invalid(path + ".physicalTableMode is required");
        }
        if (model.dataSourceId() == null) {
            throw invalid(path + ".dataSourceId is required");
        }
        if (!dataSources.containsKey(model.dataSourceId())) {
            throw invalid(path + ".dataSourceId references missing data source " + model.dataSourceId());
        }
        validateOptionalNamespace(model.catalogName(), path + ".catalogName");
        validateOptionalNamespace(model.schemaName(), path + ".schemaName");
        if (blank(model.physicalTableName())) {
            throw invalid(path + ".physicalTableName is required");
        }
        if (model.columns() == null || model.columns().isEmpty()) {
            throw invalid(path + ".columns must not be empty");
        }
        Set<String> columnNames = new HashSet<>();
        for (int columnIndex = 0; columnIndex < model.columns().size(); columnIndex++) {
            CanvasColumnSchema column = model.columns().get(columnIndex);
            String columnPath = path + ".columns[" + columnIndex + "]";
            validateColumn(column, columnPath);
            if (!columnNames.add(column.name())) {
                throw invalid(columnPath + ".name duplicates column " + column.name());
            }
        }
    }

    private static void validateOptionalNamespace(String value, String path) {
        if (value != null && value.isBlank()) {
            throw invalid(path + " must be null or nonblank");
        }
    }

    private static void validateTable(MetadataTable table, String path) {
        if (table == null || blank(table.tableName())) {
            throw invalid(path + ".tableName is required");
        }
        if (table.objectType() == null) {
            throw invalid(path + ".objectType is required");
        }
        if (table.columns() == null || table.columns().isEmpty()) {
            throw invalid(path + ".columns must not be empty");
        }
        Set<String> columnNames = new HashSet<>();
        for (int columnIndex = 0; columnIndex < table.columns().size(); columnIndex++) {
            CanvasColumnSchema column = table.columns().get(columnIndex);
            String columnPath = path + ".columns[" + columnIndex + "]";
            validateColumn(column, columnPath);
            if (!columnNames.add(column.name())) {
                throw invalid(columnPath + ".name duplicates column " + column.name());
            }
        }
    }

    private static void validateColumn(CanvasColumnSchema column, String path) {
        if (column == null || blank(column.name())) {
            throw invalid(path + ".name is required");
        }
        if (column.fieldType() == null) {
            throw invalid(path + ".fieldType is required");
        }
        if (column.fieldType() == PlatformDataType.STRING) {
            if (column.length() != null && column.length() < 1) {
                throw invalid(path + ".length must be positive");
            }
        } else if (column.length() != null) {
            throw invalid(path + ".length is only supported for STRING");
        }
        if (column.fieldType() == PlatformDataType.DECIMAL) {
            if (column.precision() == null || column.precision() < 1 || column.precision() > 38) {
                throw invalid(path + ".precision must be between 1 and 38");
            }
            if (column.scale() == null || column.scale() < 0 || column.scale() > column.precision()) {
                throw invalid(path + ".scale must be between 0 and precision");
            }
        } else if (column.precision() != null || column.scale() != null) {
            throw invalid(path + ".precision and scale are only supported for DECIMAL");
        }
    }

    private static TaskEngineException invalid(String detail) {
        return new TaskEngineException(400, "INVALID_METADATA_SNAPSHOT", "元数据快照无效", detail);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record DataSourceEntry(MetadataDataSource metadata, Map<String, MetadataTable> tables) {
        public MetadataTable table(String tableName) {
            return tables.get(tableName);
        }
    }

    public record ModelEntry(MetadataModel metadata, CanvasTableSchema tableSchema) {
    }

    public record FileDatasetTableEntry(
            MetadataFileDatasetTable metadata,
            CanvasTableSchema tableSchema
    ) {
    }
}
