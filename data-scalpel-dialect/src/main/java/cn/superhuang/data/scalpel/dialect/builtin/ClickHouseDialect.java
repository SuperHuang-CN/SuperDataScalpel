package cn.superhuang.data.scalpel.dialect.builtin;

import cn.superhuang.data.scalpel.contract.type.CrsReference;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import cn.superhuang.data.scalpel.dialect.api.ConnectionOptionChoice;
import cn.superhuang.data.scalpel.dialect.api.ConnectionOptionDefinition;
import cn.superhuang.data.scalpel.dialect.api.ConnectionOptionType;
import cn.superhuang.data.scalpel.dialect.api.NamespaceMode;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionConfig;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionSpec;
import cn.superhuang.data.scalpel.dialect.model.ColumnMetadata;
import cn.superhuang.data.scalpel.dialect.model.DdlPlan;
import cn.superhuang.data.scalpel.dialect.model.JdbcTypeDescriptor;
import cn.superhuang.data.scalpel.dialect.model.PhysicalTypeDefinition;
import cn.superhuang.data.scalpel.dialect.model.SpatialColumnMetadata;
import cn.superhuang.data.scalpel.dialect.model.SpatialMetadataStrength;
import cn.superhuang.data.scalpel.dialect.model.SpatialStorageEncoding;
import cn.superhuang.data.scalpel.dialect.model.TableChangeCheck;
import cn.superhuang.data.scalpel.dialect.model.TableChangeCheckType;
import cn.superhuang.data.scalpel.dialect.model.TableChangeExecutionMode;
import cn.superhuang.data.scalpel.dialect.model.TableChangeExecutionOption;
import cn.superhuang.data.scalpel.dialect.model.TableChangeOperation;
import cn.superhuang.data.scalpel.dialect.model.TableChangeOperationType;
import cn.superhuang.data.scalpel.dialect.model.TableChangePlan;
import cn.superhuang.data.scalpel.dialect.model.TableChangeReason;
import cn.superhuang.data.scalpel.dialect.model.TableChangeReasonCode;
import cn.superhuang.data.scalpel.dialect.model.TableChangeRisk;
import cn.superhuang.data.scalpel.dialect.model.TableChangeStrategy;
import cn.superhuang.data.scalpel.dialect.model.TableColumnDefinition;
import cn.superhuang.data.scalpel.dialect.model.TableColumnType;
import cn.superhuang.data.scalpel.dialect.model.TableDdlAtomicity;
import cn.superhuang.data.scalpel.dialect.model.TableDefinition;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import cn.superhuang.data.scalpel.dialect.model.TableMetadata;
import cn.superhuang.data.scalpel.dialect.model.TablePhysicalStatistics;
import cn.superhuang.data.scalpel.dialect.model.TableStatisticQuality;
import cn.superhuang.data.scalpel.dialect.model.TableStorageDefinition;
import cn.superhuang.data.scalpel.dialect.model.TableStorageEngine;
import cn.superhuang.data.scalpel.dialect.model.TableStorageMetadata;
import cn.superhuang.data.scalpel.dialect.model.TableStructureDifference;
import cn.superhuang.data.scalpel.dialect.model.TableStructureDifferenceType;
import cn.superhuang.data.scalpel.dialect.model.TypeMappingResult;
import cn.superhuang.data.scalpel.dialect.model.TypeMappingQuality;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Single-node MergeTree support. No replicated/distributed engine or free-form storage expression is accepted. */
public final class ClickHouseDialect extends AbstractJdbcDialect {

    private static final String WKB_MAPPING_MESSAGE =
            "ClickHouse 使用 String 保存标准二维 WKB，空间语义由列 comment 声明";

    public ClickHouseDialect() {
        super(
                "CLICKHOUSE", "ClickHouse", 8123,
                "数据库", "Schema", null, NamespaceMode.CATALOG,
                List.of(new ConnectionOptionDefinition(
                        "ssl", "使用 SSL", ConnectionOptionType.BOOLEAN, "false",
                        List.of(new ConnectionOptionChoice("true", "是"), new ConnectionOptionChoice("false", "否"))
                )),
                "com.clickhouse.jdbc.ClickHouseDriver", "`", "`", QualificationMode.CATALOG, PreviewStyle.LIMIT
        );
    }

    @Override
    public JdbcConnectionSpec createConnectionSpec(JdbcConnectionConfig config) {
        Properties properties = baseProperties(config);
        properties.setProperty("connection_timeout", "5000");
        properties.setProperty("socket_timeout", "15000");
        properties.setProperty("ssl", option(config, "ssl", "false"));
        applyConnectionOptions(
                config, properties, Set.of(),
                Set.of("connection_timeout", "socket_timeout")
        );
        String scheme = Boolean.parseBoolean(option(config, "ssl", "false")) ? "https" : "http";
        String url = "jdbc:clickhouse:" + scheme + "://" + hostForUrl(config) + ":" + config.port() + "/" + pathSegment(config.databaseName());
        return new JdbcConnectionSpec(driverClassName(), url, properties, null);
    }

    @Override
    public String resolveCatalog(JdbcConnectionConfig config, String requestedCatalog) {
        return optional(requestedCatalog) == null ? config.databaseName() : requestedCatalog.trim();
    }

    @Override
    public String resolveSchema(JdbcConnectionConfig config, String requestedSchema) {
        return null;
    }

    @Override
    public DdlPlan planCreateTable(TableDefinition definition) {
        requireMergeTreeDefinition(definition);
        List<String> columns = definition.columns().stream()
                .map(column -> quoteIdentifier(column.name()) + " " + clickHouseColumnType(column))
                .toList();
        String orderBy = definition.storage().orderByColumns().isEmpty()
                ? "tuple()"
                : "(" + definition.storage().orderByColumns().stream()
                .map(this::quoteIdentifier)
                .collect(Collectors.joining(", ")) + ")";
        return new DdlPlan(
                definition.table(),
                List.of("CREATE TABLE " + qualifiedName(definition.table()) + " (" + String.join(", ", columns)
                        + ") ENGINE = MergeTree() ORDER BY " + orderBy)
        );
    }

    @Override
    public TableStorageMetadata readTableStorageMetadata(Connection connection, TableIdentifier table) throws SQLException {
        if (table.catalog() == null || table.catalog().isBlank()) {
            throw new SQLException("ClickHouse table metadata requires a database name");
        }
        String sql = "SELECT engine, sorting_key FROM system.tables WHERE database = ? AND name = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, table.catalog());
            statement.setString(2, table.table());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("ClickHouse system.tables does not contain the target table");
                }
                return new TableStorageMetadata(resultSet.getString(1), parseSortingKey(resultSet.getString(2)));
            }
        }
    }

    @Override
    public TablePhysicalStatistics readTablePhysicalStatistics(
            Connection connection,
            TableIdentifier table,
            Duration timeout
    ) throws SQLException {
        String sql = """
                SELECT t.engine, coalesce(sum(p.rows), 0), coalesce(sum(p.bytes_on_disk), 0)
                FROM system.tables t
                LEFT JOIN system.parts p
                  ON p.database = t.database AND p.table = t.name AND p.active = 1
                WHERE t.database = ? AND t.name = ?
                GROUP BY t.engine
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(TableStatisticsJdbcSupport.timeoutSeconds(timeout));
            statement.setString(1, table.catalog());
            statement.setString(2, table.table());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return TablePhysicalStatistics.notFound();
                }
                String engine = resultSet.getString(1);
                if (engine == null || !engine.endsWith("MergeTree")) {
                    return TablePhysicalStatistics.unsupported(
                            "ClickHouse " + (engine == null ? "未知" : engine) + " 表无法确认本地物理统计"
                    );
                }
                return TablePhysicalStatistics.available(
                        TableStatisticsJdbcSupport.nullableLong(resultSet, 2),
                        TableStatisticQuality.EXACT,
                        TableStatisticsJdbcSupport.nullableLong(resultSet, 3),
                        TableStatisticQuality.EXACT
                );
            }
        }
    }

    @Override
    public List<ColumnMetadata> enrichColumnMetadata(
            Connection connection,
            TableIdentifier table,
            List<ColumnMetadata> columns
    ) throws SQLException {
        if (table.catalog() == null || table.catalog().isBlank()) {
            throw new SQLException("ClickHouse column metadata requires a database name");
        }
        String sql = "SELECT name, type, comment FROM system.columns "
                + "WHERE database = ? AND table = ? ORDER BY position";
        Map<String, ClickHouseColumnDetails> detailsByColumn = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, table.catalog());
            statement.setString(2, table.table());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String name = resultSet.getString("name");
                    detailsByColumn.put(normalize(name), new ClickHouseColumnDetails(
                            resultSet.getString("type"),
                            resultSet.getString("comment")
                    ));
                }
            }
        }

        List<ColumnMetadata> enriched = new ArrayList<>(columns.size());
        for (ColumnMetadata column : columns) {
            ClickHouseColumnDetails details = detailsByColumn.get(normalize(column.name()));
            if (details == null) {
                throw new SQLException("ClickHouse system.columns does not contain column: " + column.name());
            }
            ClickHouseWkbSpatialMarker.ParseResult marker = ClickHouseWkbSpatialMarker.parse(details.comment());
            SpatialColumnMetadata spatial = marker.present()
                    ? wkbSpatialMetadata(details.nativeType(), marker)
                    : null;
            enriched.add(column.withDialectDetails(
                    details.nativeType(),
                    isNullableType(details.nativeType()),
                    marker.humanComment(),
                    spatial
            ));
        }
        return List.copyOf(enriched);
    }

    @Override
    public TableDefinition snapshotTableDefinition(TableMetadata actual) {
        TableStorageDefinition storage = storageDefinition(actual.storage());
        List<TableColumnDefinition> columns = actual.columns().stream().map(this::snapshotColumn).toList();
        return new TableDefinition(actual.table().identifier(), columns, List.of(), storage);
    }

    @Override
    public TableChangePlan planTableChange(TableDefinition before, TableDefinition target, TableMetadata actual) {
        requireMergeTreeDefinition(before);
        requireMergeTreeDefinition(target);
        if (containsGeometry(before) || containsGeometry(target)) {
            throw new UnsupportedOperationException("ClickHouse Geometry 受管表第一版不支持物理结构变更");
        }
        if (!compareTable(before, actual).compatible()) {
            throw new IllegalArgumentException("Physical ClickHouse table structure has drifted from the source definition");
        }

        List<TableChangeOperation> operations = new ArrayList<>();
        LinkedHashSet<TableChangeCheck> checks = new LinkedHashSet<>();
        List<TableChangeReason> reasons = new ArrayList<>();
        List<String> actions = new ArrayList<>();
        checks.add(structureCheck(before));

        boolean unsupported = false;
        if (!sameStorage(before.storage(), target.storage())) {
            unsupported = true;
            TableChangeOperation operation = storageOperation(
                    TableChangeStrategy.UNSUPPORTED,
                    TableChangeRisk.CAUTION,
                    "ClickHouse 排序键或存储引擎变化需要换表复制，本步不执行"
            );
            operations.add(operation);
            reasons.addAll(operation.reasons());
        }
        if (!before.primaryKeyColumns().isEmpty() || !target.primaryKeyColumns().isEmpty()) {
            unsupported = true;
            TableChangeOperation operation = storageOperation(
                    TableChangeStrategy.UNSUPPORTED,
                    TableChangeRisk.CAUTION,
                    "ClickHouse 单机 MergeTree 不接受关系型主键约束变更"
            );
            operations.add(operation);
            reasons.addAll(operation.reasons());
        }

        Map<UUID, TableColumnDefinition> targetById = columnsById(target, "target");
        Map<String, TableColumnDefinition> targetByName = columnsByName(target);
        Map<UUID, TableColumnDefinition> beforeById = columnsById(before, "source");
        Set<String> sortingKeyColumns = before.storage().orderByColumns().stream()
                .map(ClickHouseDialect::normalize).collect(Collectors.toSet());

        for (TableColumnDefinition source : before.columns()) {
            TableColumnDefinition destination = source.columnId() == null
                    ? targetByName.get(normalize(source.name()))
                    : targetById.get(source.columnId());
            if (destination == null) {
                if (sortingKeyColumns.contains(normalize(source.name()))) {
                    unsupported = true;
                    TableChangeOperation operation = columnOperation(
                            TableChangeOperationType.DROP_COLUMN, source, null, TableChangeStrategy.UNSUPPORTED,
                            TableChangeRisk.CAUTION, "排序键字段不能删除"
                    );
                    operations.add(operation);
                    reasons.addAll(operation.reasons());
                } else {
                    TableChangeOperation operation = columnOperation(
                            TableChangeOperationType.DROP_COLUMN, source, null, TableChangeStrategy.IN_PLACE,
                            TableChangeRisk.DESTRUCTIVE, "删除字段会永久删除该列中的数据"
                    );
                    operations.add(operation);
                    reasons.addAll(operation.reasons());
                    actions.add("DROP COLUMN " + quoteIdentifier(source.name()));
                }
                continue;
            }

            if (!source.name().equalsIgnoreCase(destination.name())) {
                if (sortingKeyColumns.contains(normalize(source.name())) || sortingKeyColumns.contains(normalize(destination.name()))) {
                    unsupported = true;
                    TableChangeOperation operation = columnOperation(
                            TableChangeOperationType.RENAME_COLUMN, source, destination, TableChangeStrategy.UNSUPPORTED,
                            TableChangeRisk.CAUTION, "排序键字段不能改名"
                    );
                    operations.add(operation);
                    reasons.addAll(operation.reasons());
                } else {
                    TableChangeOperation operation = columnOperation(
                            TableChangeOperationType.RENAME_COLUMN, source, destination, TableChangeStrategy.IN_PLACE,
                            TableChangeRisk.SAFE, null
                    );
                    operations.add(operation);
                    actions.add("RENAME COLUMN " + quoteIdentifier(source.name()) + " TO " + quoteIdentifier(destination.name()));
                }
            }

            if (source.type() != destination.type()) {
                unsupported = true;
                TableChangeOperation operation = columnOperation(
                        TableChangeOperationType.ALTER_COLUMN_TYPE, sameName(source, destination), destination,
                        TableChangeStrategy.UNSUPPORTED, TableChangeRisk.CAUTION,
                        "ClickHouse 字段类型调整可能触发异步数据改写，本步不执行"
                );
                operations.add(operation);
                reasons.addAll(operation.reasons());
            }
            if (source.nullable() != destination.nullable()) {
                unsupported = true;
                TableChangeOperation operation = columnOperation(
                        TableChangeOperationType.ALTER_COLUMN_NULLABILITY, sameName(source, destination), destination,
                        TableChangeStrategy.UNSUPPORTED, TableChangeRisk.CAUTION,
                        "ClickHouse 可空性调整可能触发数据改写，本步不执行"
                );
                operations.add(operation);
                reasons.addAll(operation.reasons());
            }
        }

        for (TableColumnDefinition destination : target.columns()) {
            boolean alreadyPresent = destination.columnId() == null
                    ? columnsByName(before).containsKey(normalize(destination.name()))
                    : beforeById.containsKey(destination.columnId());
            if (alreadyPresent) {
                continue;
            }
            List<TableChangeCheck> operationChecks = destination.nullable() ? List.of() : List.of(tableEmptyCheck());
            List<TableChangeReason> operationReasons = destination.nullable() ? List.of() : List.of(new TableChangeReason(
                    TableChangeReasonCode.DATA_PRECHECK_REQUIRED,
                    "新增非空字段且没有默认值时，当前表必须为空"
            ));
            TableChangeOperation operation = new TableChangeOperation(
                    TableChangeOperationType.ADD_COLUMN,
                    null,
                    destination,
                    List.of(),
                    List.of(),
                    TableChangeStrategy.IN_PLACE,
                    destination.nullable() ? TableChangeRisk.SAFE : TableChangeRisk.CAUTION,
                    operationReasons,
                    operationChecks
            );
            operations.add(operation);
            checks.addAll(operation.checks());
            reasons.addAll(operation.reasons());
            actions.add("ADD COLUMN " + quoteIdentifier(destination.name()) + " " + clickHouseColumnType(destination));
        }

        if (operations.isEmpty()) {
            return new TableChangePlan(
                    before, target, TableChangeStrategy.METADATA_ONLY, TableChangeRisk.SAFE,
                    TableDdlAtomicity.NOT_APPLICABLE, List.of(), List.of(), List.of(), List.of()
            );
        }
        if (unsupported) {
            return new TableChangePlan(
                    before, target, TableChangeStrategy.UNSUPPORTED, highestRisk(operations),
                    TableDdlAtomicity.NOT_APPLICABLE, operations, List.copyOf(checks), List.copyOf(reasons), List.of()
            );
        }
        String statement = "ALTER TABLE " + qualifiedName(before.table()) + " " + String.join(", ", actions);
        return new TableChangePlan(
                before,
                target,
                TableChangeStrategy.IN_PLACE,
                highestRisk(operations),
                TableDdlAtomicity.ATOMIC_SINGLE_STATEMENT,
                operations,
                List.copyOf(checks),
                List.copyOf(reasons),
                List.of(new TableChangeExecutionOption(
                        TableChangeExecutionMode.IN_PLACE,
                        TableDdlAtomicity.ATOMIC_SINGLE_STATEMENT,
                        List.of(statement)
                ))
        );
    }

    @Override
    public boolean checkTableChange(Connection connection, TableIdentifier table, TableChangeCheck check) throws SQLException {
        if (check.type() != TableChangeCheckType.TABLE_EMPTY) {
            throw new UnsupportedOperationException("ClickHouse does not support change precondition: " + check.type());
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT count() = 0 FROM " + qualifiedName(table));
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() && resultSet.getBoolean(1);
        }
    }

    @Override
    protected String columnTypeSql(TableColumnDefinition column) {
        return clickHouseColumnType(column);
    }

    @Override
    protected boolean matchesColumnType(TableColumnDefinition expected, ColumnMetadata actual) {
        String type = unwrapTypeWrappers(actual.nativeType());
        return switch (expected.type()) {
            case BYTE -> type.equalsIgnoreCase("Int8");
            case SHORT -> type.equalsIgnoreCase("Int16");
            case STRING, TEXT -> type.equalsIgnoreCase("String");
            case INTEGER -> type.equalsIgnoreCase("Int32");
            case LONG -> type.equalsIgnoreCase("Int64");
            case FLOAT -> type.equalsIgnoreCase("Float32");
            case DOUBLE -> type.equalsIgnoreCase("Float64");
            case DECIMAL -> type.toLowerCase(Locale.ROOT).startsWith("decimal(");
            case BOOLEAN -> type.equalsIgnoreCase("Bool");
            case DATE -> type.equalsIgnoreCase("Date");
            case TIMESTAMP, DATETIME -> type.startsWith("DateTime");
            case TIMESTAMP_NTZ -> false;
            case BINARY -> false;
            case GEOMETRY -> matchesWkbGeometry(expected, actual);
        };
    }

    @Override
    protected boolean requiresStringLengthComparison(TableColumnDefinition expected) {
        return false;
    }

    @Override
    protected boolean shouldComparePrimaryKey(TableDefinition expected, TableMetadata actual) {
        return false;
    }

    @Override
    protected List<TableStructureDifference> storageDifferences(TableDefinition expected, TableMetadata actual) {
        if (expected.storage().engine() != TableStorageEngine.MERGE_TREE) {
            return List.of(new TableStructureDifference(
                    null, TableStructureDifferenceType.STORAGE_CONFIGURATION_MISMATCH,
                    "MERGE_TREE", expected.storage().engine().name()
            ));
        }
        TableStorageMetadata storage = actual.storage();
        boolean sameEngine = storage.engine() != null && "MergeTree".equalsIgnoreCase(storage.engine());
        boolean sameOrder = sameColumnNames(expected.storage().orderByColumns(), storage.orderByColumns());
        if (sameEngine && sameOrder) {
            return List.of();
        }
        String actualDescription = (storage.engine() == null ? "未知" : storage.engine())
                + " ORDER BY " + (storage.orderByColumns().isEmpty() ? "tuple()" : String.join(", ", storage.orderByColumns()));
        String expectedDescription = "MergeTree ORDER BY " + (expected.storage().orderByColumns().isEmpty()
                ? "tuple()" : String.join(", ", expected.storage().orderByColumns()));
        return List.of(new TableStructureDifference(
                null, TableStructureDifferenceType.STORAGE_CONFIGURATION_MISMATCH, expectedDescription, actualDescription
        ));
    }

    private static TableStorageDefinition storageDefinition(TableStorageMetadata storage) {
        if (storage.engine() == null || !"MergeTree".equalsIgnoreCase(storage.engine())) {
            throw new UnsupportedOperationException("Unsupported ClickHouse table engine for managed structure snapshot: " + storage.engine());
        }
        return TableStorageDefinition.mergeTree(storage.orderByColumns());
    }

    private TableColumnDefinition snapshotColumn(ColumnMetadata actual) {
        if (actual.spatial() != null && actual.spatial().storageEncoding() == SpatialStorageEncoding.WKB) {
            TypeMappingResult<PlatformTypeDefinition> mapping = mapWkbToPlatform(actual.nativeType(), actual.spatial());
            if (!mapping.acceptable()) {
                throw new UnsupportedOperationException(mapping.message());
            }
            return new TableColumnDefinition(
                    actual.name(),
                    TableColumnType.GEOMETRY,
                    null,
                    null,
                    null,
                    actual.nullable(),
                    null,
                    mapping.definition().geometry()
            );
        }
        String nativeType = unwrapTypeWrappers(actual.nativeType()).toLowerCase(Locale.ROOT);
        TableColumnType type = switch (nativeType) {
            case "string" -> TableColumnType.TEXT;
            case "int8" -> TableColumnType.BYTE;
            case "int16" -> TableColumnType.SHORT;
            case "int32" -> TableColumnType.INTEGER;
            case "int64" -> TableColumnType.LONG;
            case "float32" -> TableColumnType.FLOAT;
            case "float64" -> TableColumnType.DOUBLE;
            case "bool" -> TableColumnType.BOOLEAN;
            case "date", "date32" -> TableColumnType.DATE;
            default -> {
                if (nativeType.startsWith("decimal(")) {
                    yield TableColumnType.DECIMAL;
                }
                if (nativeType.startsWith("datetime")) {
                    yield TableColumnType.TIMESTAMP;
                }
                throw new UnsupportedOperationException("Unsupported ClickHouse physical column type for structure snapshot: " + actual.nativeType());
            }
        };
        Integer precision = type == TableColumnType.DECIMAL ? required(actual.precision(), "precision", actual.name()) : null;
        Integer scale = type == TableColumnType.DECIMAL ? required(actual.scale(), "scale", actual.name()) : null;
        return new TableColumnDefinition(actual.name(), type, null, precision, scale, actual.nullable());
    }

    @Override
    protected Optional<TypeMappingResult<PlatformTypeDefinition>> mapDialectTypeToPlatform(
            JdbcTypeDescriptor physicalType
    ) {
        if (physicalType.spatial() != null
                && physicalType.spatial().storageEncoding() == SpatialStorageEncoding.WKB) {
            return Optional.of(mapWkbToPlatform(physicalType.nativeTypeName(), physicalType.spatial()));
        }
        String nativeType = unwrapTypeWrappers(physicalType.nativeTypeName()).toLowerCase(Locale.ROOT);
        if (nativeType.startsWith("decimal(")) {
            Integer precision = physicalType.precision();
            Integer scale = physicalType.scale();
            if (precision == null || scale == null || precision < 1 || precision > 38 || scale < 0 || scale > precision) {
                return Optional.of(TypeMappingResult.unsupported(
                        "ClickHouse Decimal 缺少有效精度，或精度超过 38"
                ));
            }
            return Optional.of(TypeMappingResult.exact(PlatformTypeDefinition.decimal(precision, scale)));
        }
        if (nativeType.startsWith("fixedstring(")) {
            return Optional.of(TypeMappingResult.lossy(
                    PlatformTypeDefinition.string(physicalType.length()),
                    "ClickHouse FixedString 具有补零语义，不能无损映射为平台 STRING"
            ));
        }
        if (nativeType.startsWith("datetime")) {
            return Optional.of(TypeMappingResult.normalized(
                    PlatformTypeDefinition.of(PlatformDataType.TIMESTAMP),
                    "ClickHouse DateTime 按时间线时间 TIMESTAMP 归一化"
            ));
        }
        TypeMappingResult<PlatformTypeDefinition> result = switch (nativeType) {
            case "bool" -> TypeMappingResult.exact(PlatformTypeDefinition.of(PlatformDataType.BOOLEAN));
            case "int8" -> TypeMappingResult.exact(PlatformTypeDefinition.of(PlatformDataType.BYTE));
            case "uint8", "int16" -> TypeMappingResult.normalized(
                    PlatformTypeDefinition.of(PlatformDataType.SHORT),
                    nativeType + " 按可覆盖其值域的 SHORT 归一化"
            );
            case "uint16", "int32" -> TypeMappingResult.normalized(
                    PlatformTypeDefinition.of(PlatformDataType.INTEGER),
                    nativeType + " 按可覆盖其值域的 INTEGER 归一化"
            );
            case "uint32", "int64" -> TypeMappingResult.normalized(
                    PlatformTypeDefinition.of(PlatformDataType.LONG),
                    nativeType + " 按可覆盖其值域的 LONG 归一化"
            );
            case "uint64" -> TypeMappingResult.normalized(
                    PlatformTypeDefinition.decimal(20, 0),
                    "ClickHouse UInt64 按可覆盖其值域的 DECIMAL(20,0) 归一化"
            );
            case "float32" -> TypeMappingResult.exact(PlatformTypeDefinition.of(PlatformDataType.FLOAT));
            case "float64" -> TypeMappingResult.exact(PlatformTypeDefinition.of(PlatformDataType.DOUBLE));
            case "string" -> TypeMappingResult.normalized(
                    PlatformTypeDefinition.string(null), "ClickHouse String 按无长度上限的 STRING 归一化"
            );
            case "date" -> TypeMappingResult.exact(PlatformTypeDefinition.of(PlatformDataType.DATE));
            case "date32" -> TypeMappingResult.normalized(
                    PlatformTypeDefinition.of(PlatformDataType.DATE), "ClickHouse Date32 按 DATE 归一化"
            );
            default -> TypeMappingResult.unsupported(
                    "暂不支持 ClickHouse 字段类型：" + physicalType.nativeTypeName()
            );
        };
        return Optional.of(result);
    }

    @Override
    protected TypeMappingResult<PhysicalTypeDefinition> mapPlatformTypeToPhysical(
            PlatformTypeDefinition platformType
    ) {
        if (platformType.type() == PlatformDataType.GEOMETRY) {
            String issue = SpatialTypeSupport.validateV1Geometry(platformType.geometry());
            if (issue != null) {
                return TypeMappingResult.unsupported(issue);
            }
            return new TypeMappingResult<>(
                    new PhysicalTypeDefinition(
                            TableColumnType.GEOMETRY,
                            null,
                            null,
                            null,
                            platformType.geometry()
                    ),
                    TypeMappingQuality.EXACT,
                    WKB_MAPPING_MESSAGE
            );
        }
        if (platformType.type() == PlatformDataType.BINARY) {
            return TypeMappingResult.unsupported("ClickHouse 受控物理表暂不支持 BINARY 字段");
        }
        if (platformType.type() == PlatformDataType.TIMESTAMP_NTZ) {
            return TypeMappingResult.unsupported("ClickHouse DateTime 带时区解释，不能安全承载 TIMESTAMP_NTZ");
        }
        if (platformType.type() == PlatformDataType.STRING && platformType.length() != null) {
            return TypeMappingResult.lossy(
                    new PhysicalTypeDefinition(TableColumnType.TEXT, null, null, null),
                    "ClickHouse String 不保留长度约束；请改为无长度上限 STRING"
            );
        }
        return super.mapPlatformTypeToPhysical(platformType);
    }

    private static int required(Integer value, String attribute, String column) {
        if (value == null || value < 0) {
            throw new UnsupportedOperationException("ClickHouse decimal " + attribute + " is unavailable: " + column);
        }
        return value;
    }

    private static List<String> parseSortingKey(String expression) throws SQLException {
        if (expression == null || expression.isBlank() || "tuple()".equalsIgnoreCase(expression.trim())) {
            return List.of();
        }
        String candidate = expression.trim();
        if (candidate.startsWith("(") && candidate.endsWith(")")) {
            candidate = candidate.substring(1, candidate.length() - 1).trim();
        }
        if (candidate.isEmpty()) {
            return List.of();
        }
        List<String> columns = new ArrayList<>();
        for (String part : candidate.split(",", -1)) {
            String column = part.trim();
            if (column.startsWith("`") && column.endsWith("`") && column.length() > 1) {
                column = column.substring(1, column.length() - 1).replace("``", "`");
            }
            if (!column.matches("[A-Za-z][A-Za-z0-9_]{0,127}")) {
                throw new SQLException("Unsupported ClickHouse sorting-key expression: " + expression);
            }
            columns.add(column);
        }
        return List.copyOf(columns);
    }

    private static String unwrapNullable(String nativeType) {
        if (nativeType == null) {
            return "";
        }
        String value = nativeType.trim();
        if (value.regionMatches(true, 0, "Nullable(", 0, "Nullable(".length()) && value.endsWith(")")) {
            return value.substring("Nullable(".length(), value.length() - 1).trim();
        }
        return value;
    }

    private static String unwrapTypeWrappers(String nativeType) {
        String value = nativeType == null ? "" : nativeType.trim();
        boolean changed;
        do {
            String before = value;
            value = unwrapNullable(value);
            if (value.regionMatches(true, 0, "LowCardinality(", 0, "LowCardinality(".length())
                    && value.endsWith(")")) {
                value = value.substring("LowCardinality(".length(), value.length() - 1).trim();
            }
            changed = !before.equals(value);
        } while (changed);
        return value;
    }

    private static Map<UUID, TableColumnDefinition> columnsById(TableDefinition definition, String label) {
        Map<UUID, TableColumnDefinition> result = new HashMap<>();
        for (TableColumnDefinition column : definition.columns()) {
            if (column.columnId() != null && result.put(column.columnId(), column) != null) {
                throw new IllegalArgumentException("Duplicate " + label + " column identity");
            }
        }
        return result;
    }

    private static Map<String, TableColumnDefinition> columnsByName(TableDefinition definition) {
        Map<String, TableColumnDefinition> result = new HashMap<>();
        for (TableColumnDefinition column : definition.columns()) {
            result.put(normalize(column.name()), column);
        }
        return result;
    }

    private static TableChangeOperation columnOperation(
            TableChangeOperationType type,
            TableColumnDefinition before,
            TableColumnDefinition after,
            TableChangeStrategy strategy,
            TableChangeRisk risk,
            String reason
    ) {
        List<TableChangeReason> reasons = reason == null ? List.of() : List.of(new TableChangeReason(
                strategy == TableChangeStrategy.UNSUPPORTED
                        ? TableChangeReasonCode.OPERATION_UNSUPPORTED
                        : TableChangeReasonCode.POSSIBLE_DATA_LOSS,
                reason
        ));
        return new TableChangeOperation(type, before, after, List.of(), List.of(), strategy, risk, reasons, List.of());
    }

    private static TableChangeOperation storageOperation(
            TableChangeStrategy strategy,
            TableChangeRisk risk,
            String reason
    ) {
        return new TableChangeOperation(
                TableChangeOperationType.ALTER_STORAGE_LAYOUT,
                null,
                null,
                List.of(),
                List.of(),
                strategy,
                risk,
                List.of(new TableChangeReason(TableChangeReasonCode.STORAGE_LAYOUT_CHANGE, reason)),
                List.of()
        );
    }

    private static TableChangeCheck structureCheck(TableDefinition before) {
        return new TableChangeCheck(
                TableChangeCheckType.STRUCTURE_FINGERPRINT_MATCH,
                List.of(),
                null,
                null,
                null,
                before.structureFingerprint(),
                "执行前必须确认物理表结构未发生漂移"
        );
    }

    private static TableChangeCheck tableEmptyCheck() {
        return new TableChangeCheck(
                TableChangeCheckType.TABLE_EMPTY,
                List.of(),
                null,
                null,
                null,
                null,
                "当前表必须为空"
        );
    }

    private static TableColumnDefinition sameName(TableColumnDefinition source, TableColumnDefinition destination) {
        return new TableColumnDefinition(
                destination.name(), source.type(), source.length(), source.precision(), source.scale(),
                source.nullable(), source.columnId(), source.geometry()
        );
    }

    private static TableChangeRisk highestRisk(List<TableChangeOperation> operations) {
        return operations.stream().map(TableChangeOperation::risk)
                .max(java.util.Comparator.comparingInt(ClickHouseDialect::riskRank))
                .orElse(TableChangeRisk.SAFE);
    }

    private static int riskRank(TableChangeRisk risk) {
        return switch (risk) {
            case SAFE -> 0;
            case CAUTION -> 1;
            case DESTRUCTIVE -> 2;
        };
    }

    private static boolean sameStorage(TableStorageDefinition left, TableStorageDefinition right) {
        return left.engine() == right.engine() && sameColumnNames(left.orderByColumns(), right.orderByColumns());
    }

    private static boolean sameColumnNames(List<String> left, List<String> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int index = 0; index < left.size(); index++) {
            if (!normalize(left.get(index)).equals(normalize(right.get(index)))) {
                return false;
            }
        }
        return true;
    }

    private void requireMergeTreeDefinition(TableDefinition definition) {
        if (definition.storage().engine() != TableStorageEngine.MERGE_TREE) {
            throw new IllegalArgumentException("ClickHouse managed tables require a MergeTree storage definition");
        }
        if (!definition.primaryKeyColumns().isEmpty()) {
            throw new IllegalArgumentException("ClickHouse managed tables do not accept relational primary key columns");
        }
        for (TableColumnDefinition column : definition.columns()) {
            if (column.type() == TableColumnType.BINARY) {
                throw new IllegalArgumentException("ClickHouse managed tables do not support BINARY fields");
            }
        }
        Map<String, TableColumnDefinition> columns = columnsByName(definition);
        for (String orderByColumn : definition.storage().orderByColumns()) {
            TableColumnDefinition column = columns.get(normalize(orderByColumn));
            if (column != null && column.type() == TableColumnType.GEOMETRY) {
                throw new IllegalArgumentException("ClickHouse Geometry 字段不能作为 MergeTree 排序键：" + orderByColumn);
            }
        }
    }

    private String clickHouseColumnType(TableColumnDefinition column) {
        if (column.type() == TableColumnType.GEOMETRY) {
            String physicalType = column.nullable() ? "Nullable(String)" : "String";
            return physicalType + " COMMENT '" + ClickHouseWkbSpatialMarker.render(column.geometry()) + "'";
        }
        String base = switch (column.type()) {
            case BYTE -> "Int8";
            case SHORT -> "Int16";
            case STRING, TEXT -> "String";
            case INTEGER -> "Int32";
            case LONG -> "Int64";
            case FLOAT -> "Float32";
            case DOUBLE -> "Float64";
            case DECIMAL -> "Decimal(" + column.precision() + ", " + column.scale() + ")";
            case BOOLEAN -> "Bool";
            case DATE -> "Date";
            case TIMESTAMP -> "DateTime64(6, 'UTC')";
            case TIMESTAMP_NTZ -> throw new IllegalArgumentException("ClickHouse managed tables do not support TIMESTAMP_NTZ fields");
            case DATETIME -> "DateTime";
            case BINARY -> throw new IllegalArgumentException("ClickHouse managed tables do not support BINARY fields");
            case GEOMETRY -> throw new IllegalStateException("Geometry is rendered before the scalar type switch");
        };
        return column.nullable() ? "Nullable(" + base + ")" : base;
    }

    private static SpatialColumnMetadata wkbSpatialMetadata(
            String nativeType,
            ClickHouseWkbSpatialMarker.ParseResult marker
    ) {
        GeometryTypeDefinition geometry = marker.geometry();
        String issue = marker.issue();
        if (!isWkbStringType(nativeType)) {
            issue = "ClickHouse WKB 空间声明只能用于 String 或 Nullable(String) 列";
        }
        return new SpatialColumnMetadata(
                geometry == null ? null : geometry.kind().name(),
                null,
                geometry == null ? null : geometry.crs().authority(),
                geometry == null ? null : geometry.crs().code(),
                geometry == null ? null : geometry.dimension(),
                false,
                false,
                SpatialStorageEncoding.WKB,
                issue == null ? SpatialMetadataStrength.DECLARED : SpatialMetadataStrength.NONE,
                issue
        );
    }

    private static TypeMappingResult<PlatformTypeDefinition> mapWkbToPlatform(
            String nativeType,
            SpatialColumnMetadata spatial
    ) {
        if (spatial.issue() != null) {
            return TypeMappingResult.unsupported(spatial.issue());
        }
        if (!isWkbStringType(nativeType)) {
            return TypeMappingResult.unsupported(
                    "ClickHouse WKB Geometry 必须使用 String 或 Nullable(String) 物理列"
            );
        }
        if (spatial.metadataStrength() != SpatialMetadataStrength.DECLARED) {
            return TypeMappingResult.unsupported("ClickHouse WKB Geometry 缺少完整的列 comment 空间声明");
        }
        GeometryTypeDefinition geometry = geometryDefinition(spatial);
        if (geometry == null) {
            return TypeMappingResult.unsupported("ClickHouse WKB Geometry 空间声明不完整");
        }
        String issue = SpatialTypeSupport.validateV1Geometry(geometry);
        if (issue != null) {
            return TypeMappingResult.unsupported(issue);
        }
        return new TypeMappingResult<>(
                PlatformTypeDefinition.geometry(geometry),
                TypeMappingQuality.EXACT,
                WKB_MAPPING_MESSAGE
        );
    }

    private static GeometryTypeDefinition geometryDefinition(SpatialColumnMetadata spatial) {
        if (spatial.nativeGeometryKind() == null
                || spatial.crsAuthority() == null
                || spatial.crsCode() == null
                || spatial.coordinateDimension() == null) {
            return null;
        }
        try {
            return new GeometryTypeDefinition(
                    GeometryKind.valueOf(spatial.nativeGeometryKind()),
                    new CrsReference(spatial.crsAuthority(), spatial.crsCode()),
                    spatial.coordinateDimension()
            );
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static boolean matchesWkbGeometry(TableColumnDefinition expected, ColumnMetadata actual) {
        SpatialColumnMetadata spatial = actual.spatial();
        if (spatial == null || spatial.storageEncoding() != SpatialStorageEncoding.WKB) {
            return false;
        }
        TypeMappingResult<PlatformTypeDefinition> mapping = mapWkbToPlatform(actual.nativeType(), spatial);
        return mapping.acceptable() && expected.geometry().equals(mapping.definition().geometry());
    }

    private static boolean isWkbStringType(String nativeType) {
        String value = nativeType == null ? "" : nativeType.trim();
        return value.equalsIgnoreCase("String")
                || (isNullableType(value) && unwrapNullable(value).equalsIgnoreCase("String"));
    }

    private static boolean isNullableType(String nativeType) {
        String value = nativeType == null ? "" : nativeType.trim();
        return value.regionMatches(true, 0, "Nullable(", 0, "Nullable(".length())
                && value.endsWith(")");
    }

    private static boolean containsGeometry(TableDefinition definition) {
        return definition.columns().stream().anyMatch(column -> column.type() == TableColumnType.GEOMETRY);
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private record ClickHouseColumnDetails(String nativeType, String comment) {
    }
}
