package cn.superhuang.data.scalpel.dialect.builtin;

import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import cn.superhuang.data.scalpel.dialect.api.NamespaceMode;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionConfig;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionSpec;
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
import cn.superhuang.data.scalpel.dialect.model.PhysicalTypeDefinition;
import cn.superhuang.data.scalpel.dialect.model.TypeMappingResult;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

/**
 * Conservative Dameng change planner. A DDL batch is executable only when the target instance
 * proves that it does not auto-commit DDL and is not running in DPC mode.
 */
public final class DamengDialect extends AbstractJdbcDialect {

    public DamengDialect() {
        super(
                "DAMENG", "达梦", 5236,
                "数据库", "Schema", null, NamespaceMode.SCHEMA, List.of(),
                "dm.jdbc.driver.DmDriver", "\"", "\"", QualificationMode.SCHEMA, PreviewStyle.ROWNUM
        );
    }

    @Override
    public JdbcConnectionSpec createConnectionSpec(JdbcConnectionConfig config) {
        Properties properties = baseProperties(config);
        properties.setProperty("connectTimeout", "5000");
        properties.setProperty("socketTimeout", "15000");
        applyConnectionOptions(config, properties, Set.of(), Set.of("connectTimeout", "socketTimeout"));
        String url = "jdbc:dm://" + hostForUrl(config) + ":" + config.port() + "/" + pathSegment(config.databaseName());
        return new JdbcConnectionSpec(driverClassName(), url, properties, config.schemaName());
    }

    @Override
    public String resolveCatalog(JdbcConnectionConfig config, String requestedCatalog) {
        return null;
    }

    @Override
    public String resolveSchema(JdbcConnectionConfig config, String requestedSchema) {
        String resolved = super.resolveSchema(config, requestedSchema);
        return resolved == null ? config.username().toUpperCase(Locale.ROOT) : resolved;
    }

    @Override
    public TableChangePlan planTableChange(TableDefinition before, TableDefinition target, TableMetadata actual) {
        return planTableChange(before, target, actual, DamengChangeRuntime.unavailable());
    }

    @Override
    public TableChangePlan planTableChange(
            Connection connection,
            TableDefinition before,
            TableDefinition target,
            TableMetadata actual
    ) {
        try {
            return planTableChange(before, target, actual, DamengChangeRuntime.read(connection));
        } catch (SQLException exception) {
            return planTableChange(before, target, actual, DamengChangeRuntime.unavailable());
        }
    }

    /** Visible to same-package tests; production callers use the connection-aware overload. */
    TableChangePlan planTableChange(
            TableDefinition before,
            TableDefinition target,
            TableMetadata actual,
            DamengChangeRuntime runtime
    ) {
        if (!compareTable(before, actual).compatible()) {
            throw new IllegalArgumentException("Physical table structure has drifted from the source definition");
        }

        List<TableChangeOperation> operations = new ArrayList<>();
        LinkedHashSet<TableChangeCheck> checks = new LinkedHashSet<>();
        List<TableChangeReason> reasons = new ArrayList<>();
        List<String> statements = new ArrayList<>();
        Map<UUID, TableColumnDefinition> targetById = columnsById(target, "target");
        Map<String, TableColumnDefinition> targetByName = columnsByName(target);
        Map<UUID, TableColumnDefinition> beforeById = columnsById(before, "source");
        Map<String, TableColumnDefinition> beforeByName = columnsByName(before);
        boolean unsupported = false;

        checks.add(structureCheck(before));
        String table = qualifiedName(before.table());

        for (TableColumnDefinition source : before.columns()) {
            TableColumnDefinition destination = destinationFor(source, targetById, targetByName);
            if (destination == null) {
                unsupported |= addUnsupportedColumnOperation(
                        operations, reasons, TableChangeOperationType.DROP_COLUMN, source, null,
                        TableChangeRisk.DESTRUCTIVE, "删除字段需要达梦依赖对象迁移策略，本步不执行"
                );
                continue;
            }

            TableColumnDefinition effectiveSource = source;
            if (!source.name().equalsIgnoreCase(destination.name())) {
                TableChangeOperation operation = columnOperation(
                        TableChangeOperationType.RENAME_COLUMN, source, destination,
                        TableChangeStrategy.IN_PLACE, TableChangeRisk.SAFE,
                        List.of(), List.of()
                );
                addOperation(operations, checks, reasons, operation);
                statements.add("ALTER TABLE " + table + " RENAME COLUMN " + quoteIdentifier(source.name())
                        + " TO " + quoteIdentifier(destination.name()));
                effectiveSource = renamedSource(source, destination);
            }

            boolean typeOrLengthChanged = false;
            if (source.type() != destination.type()) {
                if (source.type() == TableColumnType.INTEGER && destination.type() == TableColumnType.LONG) {
                    TableChangeOperation operation = columnOperation(
                            TableChangeOperationType.ALTER_COLUMN_TYPE, effectiveSource, destination,
                            TableChangeStrategy.IN_PLACE, TableChangeRisk.CAUTION,
                            List.of(new TableChangeReason(
                                    TableChangeReasonCode.DATA_CONVERSION_REQUIRED,
                                    "达梦会校验已有整数值是否可以转换为 BIGINT"
                            )), List.of()
                    );
                    addOperation(operations, checks, reasons, operation);
                    typeOrLengthChanged = true;
                } else {
                    unsupported |= addUnsupportedColumnOperation(
                            operations, reasons, TableChangeOperationType.ALTER_COLUMN_TYPE, effectiveSource, destination,
                            TableChangeRisk.CAUTION, "当前达梦适配未定义该字段类型转换"
                    );
                }
            } else if (source.type() == TableColumnType.STRING && !source.length().equals(destination.length())) {
                if (destination.length() > source.length()) {
                    TableChangeOperation operation = columnOperation(
                            TableChangeOperationType.ALTER_COLUMN_LENGTH, effectiveSource, destination,
                            TableChangeStrategy.IN_PLACE, TableChangeRisk.SAFE, List.of(), List.of()
                    );
                    addOperation(operations, checks, reasons, operation);
                    typeOrLengthChanged = true;
                } else {
                    unsupported |= addUnsupportedColumnOperation(
                            operations, reasons, TableChangeOperationType.ALTER_COLUMN_LENGTH, effectiveSource, destination,
                            TableChangeRisk.CAUTION, "缩短字符串长度需要先进行数据迁移，本步不执行"
                    );
                }
            } else if (source.type() == TableColumnType.DECIMAL
                    && (!source.precision().equals(destination.precision()) || !source.scale().equals(destination.scale()))) {
                unsupported |= addUnsupportedColumnOperation(
                        operations, reasons, TableChangeOperationType.ALTER_COLUMN_PRECISION, effectiveSource, destination,
                        TableChangeRisk.CAUTION, "小数精度变化需要专门的数据迁移策略，本步不执行"
                );
            }

            if (typeOrLengthChanged) {
                statements.add(modifyColumnType(table, effectiveSource.name(), destination));
            }
            if (source.nullable() != destination.nullable()) {
                boolean becomingRequired = !destination.nullable();
                TableChangeOperation operation = columnOperation(
                        TableChangeOperationType.ALTER_COLUMN_NULLABILITY, effectiveSource, destination,
                        TableChangeStrategy.IN_PLACE, becomingRequired ? TableChangeRisk.CAUTION : TableChangeRisk.SAFE,
                        becomingRequired ? List.of(new TableChangeReason(
                                TableChangeReasonCode.DATA_PRECHECK_REQUIRED, "设为非空前必须确认不存在空值"
                        )) : List.of(),
                        becomingRequired ? List.of(noNullsCheck(source.name())) : List.of()
                );
                addOperation(operations, checks, reasons, operation);
                if (!typeOrLengthChanged) {
                    statements.add("ALTER TABLE " + table + " MODIFY " + quoteIdentifier(destination.name())
                            + (destination.nullable() ? " NULL" : " NOT NULL"));
                }
            }
        }

        for (TableColumnDefinition destination : target.columns()) {
            if (sourceFor(destination, beforeById, beforeByName) != null) {
                continue;
            }
            if (!isDirectlySupported(destination.type())) {
                unsupported |= addUnsupportedColumnOperation(
                        operations, reasons, TableChangeOperationType.ADD_COLUMN, null, destination,
                        TableChangeRisk.CAUTION, "当前达梦适配不支持该字段类型的受控新增"
                );
                continue;
            }
            List<TableChangeCheck> operationChecks = destination.nullable() ? List.of() : List.of(tableEmptyCheck());
            TableChangeOperation operation = columnOperation(
                    TableChangeOperationType.ADD_COLUMN, null, destination,
                    TableChangeStrategy.IN_PLACE, destination.nullable() ? TableChangeRisk.SAFE : TableChangeRisk.CAUTION,
                    destination.nullable() ? List.of() : List.of(new TableChangeReason(
                            TableChangeReasonCode.DATA_PRECHECK_REQUIRED,
                            "达梦只能在空表上新增无默认值的非空字段"
                    )), operationChecks
            );
            addOperation(operations, checks, reasons, operation);
            statements.add("ALTER TABLE " + table + " ADD COLUMN " + columnDefinitionSql(destination));
        }

        List<String> effectiveSourcePrimaryKey = effectivePrimaryKey(before, target, targetById);
        if (!sameColumns(effectiveSourcePrimaryKey, target.primaryKeyColumns())) {
            TableChangeOperationType type = effectiveSourcePrimaryKey.isEmpty()
                    ? TableChangeOperationType.ADD_PRIMARY_KEY
                    : target.primaryKeyColumns().isEmpty()
                    ? TableChangeOperationType.DROP_PRIMARY_KEY : TableChangeOperationType.REPLACE_PRIMARY_KEY;
            TableChangeOperation operation = primaryKeyOperation(
                    type, effectiveSourcePrimaryKey, target.primaryKeyColumns(),
                    TableChangeStrategy.UNSUPPORTED, TableChangeRisk.CAUTION,
                    List.of(new TableChangeReason(
                            TableChangeReasonCode.KEY_CONSTRAINT_CHANGE,
                            "达梦主键变更需要约束和依赖对象迁移策略，本步不执行"
                    )), List.of()
            );
            addOperation(operations, checks, reasons, operation);
            unsupported = true;
        }

        if (operations.isEmpty()) {
            return new TableChangePlan(
                    before, target, TableChangeStrategy.METADATA_ONLY, TableChangeRisk.SAFE,
                    TableDdlAtomicity.NOT_APPLICABLE, List.of(), List.of(), List.of(), List.of()
            );
        }
        if (!runtime.supportsTransactionalDdl()) {
            reasons.add(new TableChangeReason(TableChangeReasonCode.DDL_TRANSACTION_LIMITATION, runtime.unsupportedMessage()));
            return new TableChangePlan(
                    before, target, TableChangeStrategy.UNSUPPORTED, highestRisk(operations),
                    TableDdlAtomicity.NOT_APPLICABLE, operations, List.copyOf(checks), List.copyOf(reasons), List.of()
            );
        }
        if (unsupported) {
            return new TableChangePlan(
                    before, target, TableChangeStrategy.UNSUPPORTED, highestRisk(operations),
                    TableDdlAtomicity.NOT_APPLICABLE, operations, List.copyOf(checks), List.copyOf(reasons), List.of()
            );
        }

        checks.add(runtimeCheck());
        return new TableChangePlan(
                before, target, TableChangeStrategy.IN_PLACE, highestRisk(operations),
                TableDdlAtomicity.TRANSACTIONAL_BATCH, operations, List.copyOf(checks), List.copyOf(reasons),
                List.of(new TableChangeExecutionOption(
                        TableChangeExecutionMode.IN_PLACE, TableDdlAtomicity.TRANSACTIONAL_BATCH, statements
                ))
        );
    }

    @Override
    public boolean checkTableChange(Connection connection, TableIdentifier table, TableChangeCheck check) throws SQLException {
        if (check.type() == TableChangeCheckType.DATABASE_RUNTIME_SUPPORTED) {
            return DamengChangeRuntime.read(connection).supportsTransactionalDdl();
        }
        if (check.type() == TableChangeCheckType.STRUCTURE_FINGERPRINT_MATCH) {
            throw new IllegalArgumentException("Structure fingerprint is checked by the table operator");
        }
        String qualifiedTable = qualifiedName(table);
        String sql = switch (check.type()) {
            case TABLE_EMPTY -> "SELECT CASE WHEN COUNT(*) = 0 THEN 1 ELSE 0 END FROM " + qualifiedTable;
            case COLUMNS_HAVE_NO_NULLS -> "SELECT CASE WHEN COUNT(*) = 0 THEN 1 ELSE 0 END FROM " + qualifiedTable
                    + " WHERE " + check.columnNames().stream().map(name -> quoteIdentifier(name) + " IS NULL")
                    .collect(java.util.stream.Collectors.joining(" OR "));
            case MAX_STRING_LENGTH -> "SELECT CASE WHEN COALESCE(MAX(LENGTH(" + quoteIdentifier(check.columnNames().getFirst())
                    + ")), 0) <= " + check.lengthLimit() + " THEN 1 ELSE 0 END FROM " + qualifiedTable;
            case COLUMNS_ARE_UNIQUE, DECIMAL_VALUES_FIT, NO_EXTERNAL_DEPENDENCIES, NO_REBUILD_DEPENDENCIES ->
                    throw new UnsupportedOperationException("达梦当前计划不会生成 " + check.type() + " 检查");
            case STRUCTURE_FINGERPRINT_MATCH, DATABASE_RUNTIME_SUPPORTED ->
                    throw new IllegalStateException("Unexpected check type");
        };
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.next() && resultSet.getBoolean(1);
        }
    }

    @Override
    protected String columnTypeSql(TableColumnDefinition column) {
        return switch (column.type()) {
            case BYTE, SHORT -> "SMALLINT";
            case STRING -> "VARCHAR(" + column.length() + ")";
            case TEXT -> "CLOB";
            case INTEGER -> "INT";
            case LONG -> "BIGINT";
            case FLOAT -> "REAL";
            case DOUBLE -> "DOUBLE";
            case DECIMAL -> "DECIMAL(" + column.precision() + "," + column.scale() + ")";
            case BOOLEAN -> "BIT";
            case DATE -> "DATE";
            case TIMESTAMP -> "TIMESTAMP WITH TIME ZONE";
            case TIMESTAMP_NTZ, DATETIME -> "TIMESTAMP";
            case BINARY -> "BLOB";
            case GEOMETRY -> throw new IllegalArgumentException("Dameng managed tables do not support GEOMETRY fields");
        };
    }

    @Override
    protected TypeMappingResult<PhysicalTypeDefinition> mapPlatformTypeToPhysical(
            PlatformTypeDefinition platformType
    ) {
        if (platformType.type() == PlatformDataType.BYTE) {
            return TypeMappingResult.normalized(
                    new PhysicalTypeDefinition(TableColumnType.SHORT, null, null, null),
                    "达梦没有平台约定的 8 位整数，BYTE 使用 SMALLINT 存储"
            );
        }
        return super.mapPlatformTypeToPhysical(platformType);
    }

    private static boolean addUnsupportedColumnOperation(
            List<TableChangeOperation> operations,
            List<TableChangeReason> reasons,
            TableChangeOperationType type,
            TableColumnDefinition before,
            TableColumnDefinition after,
            TableChangeRisk risk,
            String message
    ) {
        TableChangeOperation operation = columnOperation(
                type, before, after, TableChangeStrategy.UNSUPPORTED, risk,
                List.of(new TableChangeReason(TableChangeReasonCode.OPERATION_UNSUPPORTED, message)), List.of()
        );
        operations.add(operation);
        reasons.addAll(operation.reasons());
        return true;
    }

    private static void addOperation(
            List<TableChangeOperation> operations,
            LinkedHashSet<TableChangeCheck> checks,
            List<TableChangeReason> reasons,
            TableChangeOperation operation
    ) {
        operations.add(operation);
        checks.addAll(operation.checks());
        reasons.addAll(operation.reasons());
    }

    private String modifyColumnType(String table, String columnName, TableColumnDefinition destination) {
        return "ALTER TABLE " + table + " MODIFY " + quoteIdentifier(columnName) + " " + columnTypeSql(destination)
                + (destination.nullable() ? " NULL" : " NOT NULL");
    }

    private String columnDefinitionSql(TableColumnDefinition column) {
        return quoteIdentifier(column.name()) + " " + columnTypeSql(column) + (column.nullable() ? "" : " NOT NULL");
    }

    private static boolean isDirectlySupported(TableColumnType type) {
        return type != TableColumnType.BOOLEAN && type != TableColumnType.BINARY;
    }

    private static TableChangeCheck structureCheck(TableDefinition before) {
        return new TableChangeCheck(
                TableChangeCheckType.STRUCTURE_FINGERPRINT_MATCH, List.of(), null, null, null,
                before.structureFingerprint(), "执行前必须确认物理表结构未发生漂移"
        );
    }

    private static TableChangeCheck runtimeCheck() {
        return new TableChangeCheck(TableChangeCheckType.DATABASE_RUNTIME_SUPPORTED, List.of(), null, null, null, null,
                "达梦必须满足 DDL_AUTO_COMMIT=0 且 DPC_MODE=0 才允许平台执行多步 DDL");
    }

    private static TableChangeCheck tableEmptyCheck() {
        return new TableChangeCheck(TableChangeCheckType.TABLE_EMPTY, List.of(), null, null, null, null,
                "当前表必须为空");
    }

    private static TableChangeCheck noNullsCheck(String column) {
        return new TableChangeCheck(TableChangeCheckType.COLUMNS_HAVE_NO_NULLS, List.of(column), null, null, null, null,
                "字段不能包含空值");
    }

    private static Map<UUID, TableColumnDefinition> columnsById(TableDefinition definition, String label) {
        Map<UUID, TableColumnDefinition> result = new HashMap<>();
        for (TableColumnDefinition column : definition.columns()) {
            if (column.columnId() != null && result.put(column.columnId(), column) != null) {
                throw new IllegalArgumentException("Duplicate " + label + " column identity: " + column.columnId());
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

    private static TableColumnDefinition destinationFor(
            TableColumnDefinition source,
            Map<UUID, TableColumnDefinition> targetById,
            Map<String, TableColumnDefinition> targetByName
    ) {
        return source.columnId() == null ? targetByName.get(normalize(source.name())) : targetById.get(source.columnId());
    }

    private static TableColumnDefinition sourceFor(
            TableColumnDefinition destination,
            Map<UUID, TableColumnDefinition> beforeById,
            Map<String, TableColumnDefinition> beforeByName
    ) {
        return destination.columnId() == null
                ? beforeByName.get(normalize(destination.name()))
                : beforeById.get(destination.columnId());
    }

    private static TableColumnDefinition renamedSource(TableColumnDefinition source, TableColumnDefinition destination) {
        return new TableColumnDefinition(destination.name(), source.type(), source.length(), source.precision(), source.scale(),
                source.nullable(), source.columnId());
    }

    private static List<String> effectivePrimaryKey(
            TableDefinition before,
            TableDefinition target,
            Map<UUID, TableColumnDefinition> targetById
    ) {
        List<String> result = new ArrayList<>();
        for (String primaryKeyColumn : before.primaryKeyColumns()) {
            TableColumnDefinition source = before.columns().stream()
                    .filter(column -> column.name().equalsIgnoreCase(primaryKeyColumn))
                    .findFirst().orElseThrow();
            TableColumnDefinition destination = source.columnId() == null ? null : targetById.get(source.columnId());
            result.add(destination == null ? source.name() : destination.name());
        }
        return result;
    }

    private static boolean sameColumns(List<String> first, List<String> second) {
        if (first.size() != second.size()) {
            return false;
        }
        for (int index = 0; index < first.size(); index++) {
            if (!first.get(index).equalsIgnoreCase(second.get(index))) {
                return false;
            }
        }
        return true;
    }

    private static TableChangeRisk highestRisk(List<TableChangeOperation> operations) {
        TableChangeRisk risk = TableChangeRisk.SAFE;
        for (TableChangeOperation operation : operations) {
            if (operation.risk().isAtLeastAsSevereAs(risk)) {
                risk = operation.risk();
            }
        }
        return risk;
    }

    private static TableChangeOperation columnOperation(
            TableChangeOperationType type,
            TableColumnDefinition before,
            TableColumnDefinition after,
            TableChangeStrategy strategy,
            TableChangeRisk risk,
            List<TableChangeReason> reasons,
            List<TableChangeCheck> checks
    ) {
        return new TableChangeOperation(type, before, after, List.of(), List.of(), strategy, risk, reasons, checks);
    }

    private static TableChangeOperation primaryKeyOperation(
            TableChangeOperationType type,
            List<String> before,
            List<String> after,
            TableChangeStrategy strategy,
            TableChangeRisk risk,
            List<TableChangeReason> reasons,
            List<TableChangeCheck> checks
    ) {
        return new TableChangeOperation(type, null, null, before, after, strategy, risk, reasons, checks);
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
