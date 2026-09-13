package cn.superhuang.data.scalpel.business.model.service;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceType;
import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import cn.superhuang.data.scalpel.business.model.domain.PhysicalTableMode;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import cn.superhuang.data.scalpel.dialect.api.DatabaseCapability;
import cn.superhuang.data.scalpel.dialect.api.DatabaseDialect;
import cn.superhuang.data.scalpel.dialect.api.DialectRegistry;
import cn.superhuang.data.scalpel.dialect.api.SpatialPreviewDialect;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionConfig;
import cn.superhuang.data.scalpel.dialect.model.DdlPlan;
import cn.superhuang.data.scalpel.dialect.model.ColumnMetadata;
import cn.superhuang.data.scalpel.dialect.model.JdbcTypeDescriptor;
import cn.superhuang.data.scalpel.dialect.model.PhysicalTypeDefinition;
import cn.superhuang.data.scalpel.dialect.model.TableChangePlan;
import cn.superhuang.data.scalpel.dialect.model.TableChangeExecutionMode;
import cn.superhuang.data.scalpel.dialect.model.TableColumnDefinition;
import cn.superhuang.data.scalpel.dialect.model.TableDefinition;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import cn.superhuang.data.scalpel.dialect.model.TableMetadata;
import cn.superhuang.data.scalpel.dialect.model.TablePhysicalStatistics;
import cn.superhuang.data.scalpel.dialect.model.TableStorageDefinition;
import cn.superhuang.data.scalpel.dialect.model.TableStructureComparison;
import cn.superhuang.data.scalpel.dialect.model.TableStructureDifference;
import cn.superhuang.data.scalpel.dialect.model.TableStructureDifferenceType;
import cn.superhuang.data.scalpel.dialect.model.TypeMappingResult;
import cn.superhuang.data.scalpel.dialect.runtime.DatabaseAccessException;
import cn.superhuang.data.scalpel.dialect.runtime.DatabaseInspector;
import cn.superhuang.data.scalpel.dialect.runtime.DatabaseStandardQueryExecutor;
import cn.superhuang.data.scalpel.dialect.runtime.DatabaseSpatialPreviewExecutor;
import cn.superhuang.data.scalpel.dialect.model.SpatialPreviewColumn;
import cn.superhuang.data.scalpel.dialect.model.SpatialPreviewData;
import cn.superhuang.data.scalpel.dialect.model.SpatialPreviewLimits;
import cn.superhuang.data.scalpel.dialect.model.SpatialPreviewMetadata;
import cn.superhuang.data.scalpel.dialect.model.SpatialPreviewViewport;
import cn.superhuang.data.scalpel.dialect.runtime.DatabaseTableOperator;
import cn.superhuang.data.scalpel.dialect.query.StandardQuery;
import cn.superhuang.data.scalpel.dialect.query.StandardQueryResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.time.Duration;

/** Uses the registered JDBC dialect only for validated model definitions; it never accepts ad-hoc SQL. */
@Component
public class JdbcModelPhysicalTablePort implements ModelPhysicalTablePort {

    private final DialectRegistry registry;
    private final DatabaseInspector inspector;
    private final DatabaseTableOperator tableOperator;
    private final DatabaseStandardQueryExecutor queryExecutor;
    private final DatabaseSpatialPreviewExecutor spatialPreviewExecutor;

    public JdbcModelPhysicalTablePort(
            DialectRegistry registry,
            DatabaseInspector inspector,
            DatabaseTableOperator tableOperator,
            DatabaseStandardQueryExecutor queryExecutor,
            DatabaseSpatialPreviewExecutor spatialPreviewExecutor
    ) {
        this.registry = registry;
        this.inspector = inspector;
        this.tableOperator = tableOperator;
        this.queryExecutor = queryExecutor;
        this.spatialPreviewExecutor = spatialPreviewExecutor;
    }

    @Override
    public ModelPhysicalTableInspection inspect(DataSource dataSource, DataModel model, List<DataModelField> fields) {
        DatabaseDialect dialect = registry.require(dataSource.getType().name());
        TableIdentifier table = tableIdentifier(dialect, dataSource, model);
        boolean createSupported = dialect.definition().capabilities().contains(DatabaseCapability.CREATE_TABLE);
        if (!dataSource.getType().isJdbc()) {
            return unsupported(table, "当前数据源类型不支持物理表操作");
        }
        if (!dataSource.isEnabled()) {
            return new ModelPhysicalTableInspection(
                    table, PhysicalTableState.UNREACHABLE, createSupported, "关联的数据存储已停用", List.of()
            );
        }
        try {
            TableMetadata metadata = inspector.readTable(
                    dataSource.getType().name(), dataSource.getConnection().toJdbcConnectionConfig(), table
            );
            return inspect(dataSource, model, fields, metadata);
        } catch (IllegalArgumentException exception) {
            return new ModelPhysicalTableInspection(
                    table, PhysicalTableState.UNSUPPORTED, createSupported, exception.getMessage(), List.of()
            );
        } catch (DatabaseAccessException exception) {
            if ("TABLE_NOT_FOUND".equals(exception.code())) {
                return new ModelPhysicalTableInspection(table, PhysicalTableState.NOT_FOUND, createSupported, "物理表不存在", List.of());
            }
            return new ModelPhysicalTableInspection(table, PhysicalTableState.UNREACHABLE, createSupported, exception.getMessage(), List.of());
        }
    }

    @Override
    public TablePhysicalStatistics readStatistics(DataSource dataSource, DataModel model, Duration timeout) {
        if (!dataSource.getType().isJdbc()) {
            return TablePhysicalStatistics.unsupported("当前数据源类型不支持物理表统计");
        }
        DatabaseDialect dialect = registry.require(dataSource.getType().name());
        if (!dialect.definition().capabilities().contains(DatabaseCapability.READ_TABLE_STATISTICS)) {
            return TablePhysicalStatistics.unsupported("当前数据库方言不支持物理表统计");
        }
        try {
            JdbcConnectionConfig connectionConfig = dataSource.getConnection().toJdbcConnectionConfig();
            TableIdentifier table = new TableIdentifier(
                    dialect.resolveCatalog(connectionConfig, model.getCatalogName()),
                    dialect.resolveSchema(connectionConfig, model.getSchemaName()),
                    model.getPhysicalTableName()
            );
            return inspector.readTablePhysicalStatistics(
                    dataSource.getType().name(), connectionConfig, table, timeout
            );
        } catch (DatabaseAccessException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw new DatabaseAccessException("INVALID_CONNECTION_CONFIG", "数据源连接配置无效", exception);
        }
    }

    @Override
    public ModelPhysicalTableInspection inspect(
            DataSource dataSource,
            DataModel model,
            List<DataModelField> fields,
            TableMetadata metadata
    ) {
        DatabaseDialect dialect = registry.require(dataSource.getType().name());
        TableIdentifier table = tableIdentifier(dialect, dataSource, model);
        boolean createSupported = dialect.definition().capabilities().contains(DatabaseCapability.CREATE_TABLE);
        if (!dataSource.getType().isJdbc()) {
            return unsupported(table, "当前数据源类型不支持物理表操作");
        }
        if (!dataSource.isEnabled()) {
            return new ModelPhysicalTableInspection(
                    table, PhysicalTableState.UNREACHABLE, createSupported, "关联的数据存储已停用", List.of()
            );
        }
        try {
            TableStructureComparison comparison = model.getPhysicalTableMode() == PhysicalTableMode.EXTERNAL
                    ? compareExternalTable(dialect, fields, metadata)
                    : dialect.compareTable(definition(dialect, dataSource, model, table, fields), metadata);
            return new ModelPhysicalTableInspection(
                    table,
                    comparison.compatible() ? PhysicalTableState.MATCHED : PhysicalTableState.DRIFTED,
                    createSupported,
                    comparison.compatible() ? "物理表结构与模型字段一致" : "物理表结构与模型字段不一致",
                    comparison.differences()
            );
        } catch (IllegalArgumentException exception) {
            return new ModelPhysicalTableInspection(
                    table, PhysicalTableState.UNSUPPORTED, createSupported, exception.getMessage(), List.of()
            );
        }
    }

    @Override
    public TableMetadata readExternalTable(DataSource dataSource, DataModel model) {
        if (!dataSource.getType().isJdbc()) {
            throw new DatabaseAccessException("UNSUPPORTED_CONNECTION", "当前数据源类型不支持读取外部表", null);
        }
        DatabaseDialect dialect = registry.require(dataSource.getType().name());
        return inspector.readTable(
                dataSource.getType().name(), dataSource.getConnection().toJdbcConnectionConfig(),
                tableIdentifier(dialect, dataSource, model)
        );
    }

    @Override
    public TableMetadata readExternalTable(DataSource dataSource, TableIdentifier table) {
        if (!dataSource.getType().isJdbc()) {
            throw new DatabaseAccessException("UNSUPPORTED_CONNECTION", "当前数据源类型不支持读取外部表", null);
        }
        return inspector.readTable(
                dataSource.getType().name(), dataSource.getConnection().toJdbcConnectionConfig(), table
        );
    }

    @Override
    public DdlPlan planCreate(DataSource dataSource, DataModel model, List<DataModelField> fields) {
        DatabaseDialect dialect = registry.require(dataSource.getType().name());
        if (!dialect.definition().capabilities().contains(DatabaseCapability.CREATE_TABLE)) {
            throw new DatabaseAccessException(
                    "DDL_NOT_SUPPORTED", dataSource.getType().displayName() + "暂不支持由平台创建物理表", null
            );
        }
        TableIdentifier table = tableIdentifier(dialect, dataSource, model);
        return tableOperator.planCreateTable(
                dataSource.getType().name(), dataSource.getConnection().toJdbcConnectionConfig(),
                definition(dialect, dataSource, model, table, fields)
        );
    }

    @Override
    public TableChangePlan planChange(
            DataSource dataSource,
            DataModel model,
            TableDefinition before,
            TableDefinition target
    ) {
        try {
            return tableOperator.planTableChange(
                    dataSource.getType().name(), dataSource.getConnection().toJdbcConnectionConfig(), before, target
            );
        } catch (DatabaseAccessException exception) {
            throw exception;
        } catch (UnsupportedOperationException exception) {
            throw new UnsupportedOperationException(dataSource.getType().displayName() + "暂不支持物理表变更规划", exception);
        }
    }

    @Override
    public void executeChange(
            DataSource dataSource,
            DataModel model,
            TableChangePlan plan,
            TableChangeExecutionMode mode
    ) {
        tableOperator.executeTableChange(
                dataSource.getType().name(), dataSource.getConnection().toJdbcConnectionConfig(), plan, mode
        );
    }

    @Override
    public ModelPhysicalTableInspection create(DataSource dataSource, DataModel model, List<DataModelField> fields) {
        DatabaseDialect dialect = registry.require(dataSource.getType().name());
        ModelPhysicalTableInspection before = inspect(dataSource, model, fields);
        if (before.state() != PhysicalTableState.NOT_FOUND) {
            return before;
        }
        try {
            tableOperator.createTable(
                    dataSource.getType().name(), dataSource.getConnection().toJdbcConnectionConfig(),
                    definition(dialect, dataSource, model, before.table(), fields)
            );
        } catch (DatabaseAccessException exception) {
            if (!"TABLE_ALREADY_EXISTS".equals(exception.code())) {
                return new ModelPhysicalTableInspection(
                        before.table(),
                        "DDL_NOT_SUPPORTED".equals(exception.code()) ? PhysicalTableState.UNSUPPORTED : PhysicalTableState.UNREACHABLE,
                        before.createSupported(), exception.getMessage(), List.of()
                );
            }
        }
        return inspect(dataSource, model, fields);
    }

    @Override
    public StandardQueryResult query(
            DataSource dataSource,
            DataModel model,
            StandardQuery query,
            int maximumRows,
            Duration timeout
    ) {
        if (!dataSource.getType().isJdbc()) {
            throw new DatabaseAccessException("UNSUPPORTED_CONNECTION", "当前数据源类型不支持数据查询", null);
        }
        return queryExecutor.execute(
                dataSource.getType().name(), dataSource.getConnection().toJdbcConnectionConfig(), query, maximumRows, timeout
        );
    }

    @Override
    public SpatialPreviewMetadata inspectSpatialPreview(
            DataSource dataSource,
            DataModel model,
            List<SpatialPreviewColumn> columns,
            Duration timeout
    ) {
        DatabaseDialect dialect = registry.require(dataSource.getType().name());
        if (!(dialect instanceof SpatialPreviewDialect)) {
            return SpatialPreviewMetadata.unsupported("当前数据库不支持动态空间预览");
        }
        return spatialPreviewExecutor.inspect(
                dataSource.getType().name(), dataSource.getConnection().toJdbcConnectionConfig(),
                tableIdentifier(dialect, dataSource, model), columns, timeout
        );
    }

    @Override
    public SpatialPreviewData readSpatialPreview(
            DataSource dataSource,
            DataModel model,
            SpatialPreviewColumn column,
            SpatialPreviewViewport viewport,
            SpatialPreviewLimits limits,
            Duration timeout
    ) {
        DatabaseDialect dialect = registry.require(dataSource.getType().name());
        if (!(dialect instanceof SpatialPreviewDialect)) {
            throw new UnsupportedOperationException("当前数据库不支持动态空间预览");
        }
        return spatialPreviewExecutor.read(
                dataSource.getType().name(), dataSource.getConnection().toJdbcConnectionConfig(),
                tableIdentifier(dialect, dataSource, model), column, viewport, limits, timeout
        );
    }

    private static ModelPhysicalTableInspection unsupported(TableIdentifier table, String message) {
        return new ModelPhysicalTableInspection(table, PhysicalTableState.UNSUPPORTED, false, message, List.of());
    }

    private static TableIdentifier tableIdentifier(DatabaseDialect dialect, DataSource dataSource, DataModel model) {
        JdbcConnectionConfig config = dataSource.getConnection().toJdbcConnectionConfig();
        return new TableIdentifier(
                dialect.resolveCatalog(config, model.getCatalogName()),
                dialect.resolveSchema(config, model.getSchemaName()),
                model.getPhysicalTableName()
        );
    }

    private static TableDefinition definition(
            DatabaseDialect dialect,
            DataSource dataSource,
            DataModel model,
            TableIdentifier table,
            List<DataModelField> fields
    ) {
        return new TableDefinition(
                table,
                fields.stream().map(field -> physicalColumn(dialect, field)).toList(),
                dataSource.getType() == DataSourceType.CLICKHOUSE
                        ? List.of()
                        : fields.stream().filter(DataModelField::isPrimaryKey).map(DataModelField::getCode).toList(),
                dataSource.getType() == DataSourceType.CLICKHOUSE
                        ? TableStorageDefinition.mergeTree(model.getClickHouseOrderByColumns())
                        : TableStorageDefinition.none()
        );
    }

    private static TableColumnDefinition physicalColumn(DatabaseDialect dialect, DataModelField field) {
        TypeMappingResult<PhysicalTypeDefinition> mapping = dialect.mapToPhysicalType(platformType(
                field.getFieldType(), field.getLength(), field.getPrecision(), field.getScale(), field.getGeometry()
        ));
        if (!mapping.acceptable()) {
            throw new IllegalArgumentException(mapping.message());
        }
        return mapping.definition().column(field.getCode(), field.isNullable(), field.getId());
    }

    private static PlatformTypeDefinition platformType(
            PlatformDataType type,
            Integer length,
            Integer precision,
            Integer scale,
            cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition geometry
    ) {
        return switch (type) {
            case STRING -> PlatformTypeDefinition.string(length);
            case DECIMAL -> PlatformTypeDefinition.decimal(precision, scale);
            case GEOMETRY -> PlatformTypeDefinition.geometry(geometry);
            default -> PlatformTypeDefinition.of(type);
        };
    }

    private static TableStructureComparison compareExternalTable(
            DatabaseDialect dialect,
            List<DataModelField> fields,
            TableMetadata metadata
    ) {
        Map<String, ColumnMetadata> actualByName = new HashMap<>();
        metadata.columns().forEach(column -> actualByName.put(normalize(column.name()), column));
        Set<String> expectedNames = new HashSet<>();
        List<TableStructureDifference> differences = new ArrayList<>();
        for (DataModelField field : fields) {
            String name = normalize(field.getCode());
            expectedNames.add(name);
            ColumnMetadata actual = actualByName.get(name);
            if (actual == null) {
                differences.add(new TableStructureDifference(
                        field.getCode(), TableStructureDifferenceType.MISSING_COLUMN,
                        field.getFieldType().name(), "—"
                ));
                continue;
            }
            TypeMappingResult<PlatformTypeDefinition> mapping = dialect.mapToPlatformType(JdbcTypeDescriptor.from(actual));
            PlatformTypeDefinition expected = platformType(
                    field.getFieldType(), field.getLength(), field.getPrecision(), field.getScale(), field.getGeometry()
            );
            if (!mapping.acceptable() || !expected.equals(mapping.definition())) {
                differences.add(new TableStructureDifference(
                        field.getCode(), TableStructureDifferenceType.TYPE_MISMATCH,
                        expected.toString(), actual.nativeType()
                ));
                continue;
            }
            if (field.isNullable() != actual.nullable()) {
                differences.add(new TableStructureDifference(
                        field.getCode(), TableStructureDifferenceType.NULLABILITY_MISMATCH,
                        field.isNullable() ? "可为空" : "非空",
                        actual.nullable() ? "可为空" : "非空"
                ));
            }
        }
        metadata.columns().stream()
                .filter(column -> !expectedNames.contains(normalize(column.name())))
                .forEach(column -> differences.add(new TableStructureDifference(
                        column.name(), TableStructureDifferenceType.EXTRA_COLUMN, "—", column.nativeType()
                )));
        Set<String> expectedPrimaryKeys = new HashSet<>(fields.stream()
                .filter(DataModelField::isPrimaryKey).map(field -> normalize(field.getCode())).toList());
        Set<String> actualPrimaryKeys = new HashSet<>(metadata.primaryKey().columns().stream()
                .map(JdbcModelPhysicalTablePort::normalize).toList());
        if (!expectedPrimaryKeys.equals(actualPrimaryKeys)) {
            differences.add(new TableStructureDifference(
                    null, TableStructureDifferenceType.PRIMARY_KEY_MISMATCH,
                    String.join(", ", expectedPrimaryKeys), String.join(", ", actualPrimaryKeys)
            ));
        }
        return new TableStructureComparison(differences);
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
