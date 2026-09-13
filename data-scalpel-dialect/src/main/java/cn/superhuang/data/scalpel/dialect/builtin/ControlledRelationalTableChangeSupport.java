package cn.superhuang.data.scalpel.dialect.builtin;

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

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Shared conservative table-change planning for relational dialects with a proven ALTER subset. */
final class ControlledRelationalTableChangeSupport {

    enum SqlStyle {
        MYSQL_SINGLE_STATEMENT,
        POSTGRESQL_COMPATIBLE
    }

    private ControlledRelationalTableChangeSupport() {
    }

    static TableChangePlan plan(
            AbstractJdbcDialect dialect,
            TableDefinition before,
            TableDefinition target,
            TableMetadata actual,
            SqlStyle sqlStyle,
            String databaseName
    ) {
        if (!dialect.compareTable(before, actual).compatible()) {
            throw new IllegalArgumentException("Physical table structure has drifted from the source definition");
        }

        List<TableChangeOperation> operations = new ArrayList<>();
        LinkedHashSet<TableChangeCheck> checks = new LinkedHashSet<>();
        List<TableChangeReason> reasons = new ArrayList<>();
        List<String> statements = new ArrayList<>();
        List<String> mysqlClauses = new ArrayList<>();
        Map<UUID, TableColumnDefinition> beforeById = columnsById(before, "source");
        Map<UUID, TableColumnDefinition> targetById = columnsById(target, "target");
        Map<String, TableColumnDefinition> beforeByName = columnsByName(before);
        Map<String, TableColumnDefinition> targetByName = columnsByName(target);
        String table = dialect.qualifiedName(before.table());
        boolean unsupported = false;

        checks.add(structureCheck(before));
        for (TableColumnDefinition source : before.columns()) {
            TableColumnDefinition destination = destinationFor(source, targetById, targetByName);
            if (destination == null) {
                unsupported |= addUnsupportedColumnOperation(
                        operations, reasons, TableChangeOperationType.DROP_COLUMN, source, null,
                        TableChangeRisk.DESTRUCTIVE, "删除字段需要数据迁移和依赖对象处理，本批次不执行"
                );
                continue;
            }

            boolean renamed = !source.name().equalsIgnoreCase(destination.name());
            TableColumnDefinition effectiveSource = renamed ? renamedSource(source, destination) : source;
            boolean typeChanged = source.type() != destination.type();
            boolean lengthChanged = source.type() == TableColumnType.STRING
                    && destination.type() == TableColumnType.STRING
                    && !source.length().equals(destination.length());
            boolean precisionChanged = source.type() == TableColumnType.DECIMAL
                    && destination.type() == TableColumnType.DECIMAL
                    && (!source.precision().equals(destination.precision())
                    || !source.scale().equals(destination.scale()));
            boolean nullabilityChanged = source.nullable() != destination.nullable();
            boolean sourceUnsupported = false;

            if (renamed) {
                addOperation(operations, checks, reasons, columnOperation(
                        TableChangeOperationType.RENAME_COLUMN, source, destination,
                        TableChangeStrategy.IN_PLACE, TableChangeRisk.SAFE, List.of(), List.of()
                ));
            }

            if (typeChanged) {
                if (source.type() == TableColumnType.INTEGER && destination.type() == TableColumnType.LONG) {
                    addOperation(operations, checks, reasons, columnOperation(
                            TableChangeOperationType.ALTER_COLUMN_TYPE, effectiveSource, destination,
                            TableChangeStrategy.IN_PLACE, TableChangeRisk.SAFE,
                            List.of(new TableChangeReason(
                                    TableChangeReasonCode.DATA_CONVERSION_REQUIRED,
                                    "整数字段扩为长整数"
                            )), List.of()
                    ));
                } else {
                    unsupported |= addUnsupportedColumnOperation(
                            operations, reasons, TableChangeOperationType.ALTER_COLUMN_TYPE,
                            effectiveSource, destination, TableChangeRisk.CAUTION,
                            "当前" + databaseName + "适配未定义该字段类型转换"
                    );
                    sourceUnsupported = true;
                }
            } else if (lengthChanged) {
                if (destination.length() > source.length()) {
                    addOperation(operations, checks, reasons, columnOperation(
                            TableChangeOperationType.ALTER_COLUMN_LENGTH, effectiveSource, destination,
                            TableChangeStrategy.IN_PLACE, TableChangeRisk.SAFE, List.of(), List.of()
                    ));
                } else {
                    unsupported |= addUnsupportedColumnOperation(
                            operations, reasons, TableChangeOperationType.ALTER_COLUMN_LENGTH,
                            effectiveSource, destination, TableChangeRisk.CAUTION,
                            "缩短字符串长度需要数据迁移，本批次不执行"
                    );
                    sourceUnsupported = true;
                }
            } else if (precisionChanged) {
                unsupported |= addUnsupportedColumnOperation(
                        operations, reasons, TableChangeOperationType.ALTER_COLUMN_PRECISION,
                        effectiveSource, destination, TableChangeRisk.CAUTION,
                        "小数精度变化需要专门的数据校验和迁移策略，本批次不执行"
                );
                sourceUnsupported = true;
            }

            if (nullabilityChanged) {
                boolean becomingRequired = !destination.nullable();
                List<TableChangeCheck> operationChecks = becomingRequired
                        ? List.of(noNullsCheck(source.name())) : List.of();
                addOperation(operations, checks, reasons, columnOperation(
                        TableChangeOperationType.ALTER_COLUMN_NULLABILITY, effectiveSource, destination,
                        TableChangeStrategy.IN_PLACE,
                        becomingRequired ? TableChangeRisk.CAUTION : TableChangeRisk.SAFE,
                        becomingRequired ? List.of(new TableChangeReason(
                                TableChangeReasonCode.DATA_PRECHECK_REQUIRED,
                                "设为非空前必须确认不存在空值"
                        )) : List.of(), operationChecks
                ));
            }

            if (!sourceUnsupported && (renamed || typeChanged || lengthChanged || nullabilityChanged)) {
                if (sqlStyle == SqlStyle.MYSQL_SINGLE_STATEMENT) {
                    mysqlClauses.add("CHANGE COLUMN " + dialect.quoteIdentifier(source.name()) + " "
                            + columnDefinitionSql(dialect, destination));
                } else {
                    if (renamed) {
                        statements.add("ALTER TABLE " + table + " RENAME COLUMN "
                                + dialect.quoteIdentifier(source.name()) + " TO "
                                + dialect.quoteIdentifier(destination.name()));
                    }
                    if (typeChanged || lengthChanged) {
                        statements.add("ALTER TABLE " + table + " ALTER COLUMN "
                                + dialect.quoteIdentifier(destination.name()) + " TYPE "
                                + dialect.columnTypeSql(destination));
                    }
                    if (nullabilityChanged) {
                        statements.add("ALTER TABLE " + table + " ALTER COLUMN "
                                + dialect.quoteIdentifier(destination.name())
                                + (destination.nullable() ? " DROP NOT NULL" : " SET NOT NULL"));
                    }
                }
            }
        }

        for (TableColumnDefinition destination : target.columns()) {
            if (sourceFor(destination, beforeById, beforeByName) != null) {
                continue;
            }
            if (destination.type() == TableColumnType.GEOMETRY) {
                unsupported |= addUnsupportedColumnOperation(
                        operations, reasons, TableChangeOperationType.ADD_COLUMN, null, destination,
                        TableChangeRisk.CAUTION, "当前" + databaseName + "适配未开放受管 Geometry 字段"
                );
                continue;
            }
            List<TableChangeCheck> operationChecks = destination.nullable()
                    ? List.of() : List.of(tableEmptyCheck());
            addOperation(operations, checks, reasons, columnOperation(
                    TableChangeOperationType.ADD_COLUMN, null, destination,
                    TableChangeStrategy.IN_PLACE,
                    destination.nullable() ? TableChangeRisk.SAFE : TableChangeRisk.CAUTION,
                    destination.nullable() ? List.of() : List.of(new TableChangeReason(
                            TableChangeReasonCode.DATA_PRECHECK_REQUIRED,
                            "新增无默认值的非空字段时，当前表必须为空"
                    )), operationChecks
            ));
            String clause = "ADD COLUMN " + columnDefinitionSql(dialect, destination);
            if (sqlStyle == SqlStyle.MYSQL_SINGLE_STATEMENT) {
                mysqlClauses.add(clause);
            } else {
                statements.add("ALTER TABLE " + table + " " + clause);
            }
        }

        List<String> effectiveSourcePrimaryKey = effectivePrimaryKey(before, targetById);
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
                            "主键变化需要约束和依赖对象迁移策略，本批次不执行"
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
        if (unsupported) {
            return new TableChangePlan(
                    before, target, TableChangeStrategy.UNSUPPORTED, highestRisk(operations),
                    TableDdlAtomicity.NOT_APPLICABLE, operations, List.copyOf(checks),
                    List.copyOf(reasons), List.of()
            );
        }

        TableDdlAtomicity atomicity;
        if (sqlStyle == SqlStyle.MYSQL_SINGLE_STATEMENT) {
            statements = List.of("ALTER TABLE " + table + " " + String.join(", ", mysqlClauses));
            atomicity = TableDdlAtomicity.ATOMIC_SINGLE_STATEMENT;
        } else {
            atomicity = TableDdlAtomicity.TRANSACTIONAL_BATCH;
        }
        return new TableChangePlan(
                before, target, TableChangeStrategy.IN_PLACE, highestRisk(operations), atomicity,
                operations, List.copyOf(checks), List.copyOf(reasons),
                List.of(new TableChangeExecutionOption(
                        TableChangeExecutionMode.IN_PLACE, atomicity, statements
                ))
        );
    }

    static boolean check(
            AbstractJdbcDialect dialect,
            Connection connection,
            TableIdentifier table,
            TableChangeCheck check,
            String databaseName
    ) throws SQLException {
        if (check.type() == TableChangeCheckType.STRUCTURE_FINGERPRINT_MATCH) {
            throw new IllegalArgumentException("Structure fingerprint is checked by the table operator");
        }
        String qualifiedTable = dialect.qualifiedName(table);
        String sql = switch (check.type()) {
            case TABLE_EMPTY -> "SELECT CASE WHEN COUNT(*) = 0 THEN 1 ELSE 0 END FROM " + qualifiedTable;
            case COLUMNS_HAVE_NO_NULLS -> "SELECT CASE WHEN COUNT(*) = 0 THEN 1 ELSE 0 END FROM "
                    + qualifiedTable + " WHERE " + check.columnNames().stream()
                    .map(name -> dialect.quoteIdentifier(name) + " IS NULL")
                    .collect(java.util.stream.Collectors.joining(" OR "));
            case COLUMNS_ARE_UNIQUE, MAX_STRING_LENGTH, DECIMAL_VALUES_FIT,
                    NO_EXTERNAL_DEPENDENCIES, NO_REBUILD_DEPENDENCIES, DATABASE_RUNTIME_SUPPORTED ->
                    throw new UnsupportedOperationException(
                            databaseName + "当前计划不会生成 " + check.type() + " 检查"
                    );
            case STRUCTURE_FINGERPRINT_MATCH -> throw new IllegalStateException("Unexpected check type");
        };
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.next() && resultSet.getBoolean(1);
        }
    }

    private static String columnDefinitionSql(AbstractJdbcDialect dialect, TableColumnDefinition column) {
        return dialect.quoteIdentifier(column.name()) + " " + dialect.columnTypeSql(column)
                + (column.nullable() ? "" : " NOT NULL");
    }

    private static Map<UUID, TableColumnDefinition> columnsById(TableDefinition definition, String label) {
        Map<UUID, TableColumnDefinition> result = new LinkedHashMap<>();
        for (TableColumnDefinition column : definition.columns()) {
            if (column.columnId() != null && result.put(column.columnId(), column) != null) {
                throw new IllegalArgumentException("Duplicate " + label + " column identity: " + column.columnId());
            }
        }
        return result;
    }

    private static Map<String, TableColumnDefinition> columnsByName(TableDefinition definition) {
        Map<String, TableColumnDefinition> result = new LinkedHashMap<>();
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
        return source.columnId() == null
                ? targetByName.get(normalize(source.name())) : targetById.get(source.columnId());
    }

    private static TableColumnDefinition sourceFor(
            TableColumnDefinition destination,
            Map<UUID, TableColumnDefinition> beforeById,
            Map<String, TableColumnDefinition> beforeByName
    ) {
        return destination.columnId() == null
                ? beforeByName.get(normalize(destination.name())) : beforeById.get(destination.columnId());
    }

    private static TableColumnDefinition renamedSource(
            TableColumnDefinition source,
            TableColumnDefinition destination
    ) {
        return new TableColumnDefinition(
                destination.name(), source.type(), source.length(), source.precision(), source.scale(),
                source.nullable(), source.columnId()
        );
    }

    private static List<String> effectivePrimaryKey(
            TableDefinition before,
            Map<UUID, TableColumnDefinition> targetById
    ) {
        List<String> result = new ArrayList<>();
        Map<String, TableColumnDefinition> beforeByName = columnsByName(before);
        for (String key : before.primaryKeyColumns()) {
            TableColumnDefinition source = beforeByName.get(normalize(key));
            TableColumnDefinition destination = source == null || source.columnId() == null
                    ? null : targetById.get(source.columnId());
            result.add(destination == null ? key : destination.name());
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

    private static TableChangeCheck structureCheck(TableDefinition before) {
        return new TableChangeCheck(
                TableChangeCheckType.STRUCTURE_FINGERPRINT_MATCH,
                List.of(), null, null, null, before.structureFingerprint(),
                "执行前必须确认物理表结构未发生漂移"
        );
    }

    private static TableChangeCheck tableEmptyCheck() {
        return new TableChangeCheck(
                TableChangeCheckType.TABLE_EMPTY, List.of(), null, null, null, null,
                "当前表必须为空"
        );
    }

    private static TableChangeCheck noNullsCheck(String columnName) {
        return new TableChangeCheck(
                TableChangeCheckType.COLUMNS_HAVE_NO_NULLS, List.of(columnName),
                null, null, null, null, "字段不能包含空值"
        );
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

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
