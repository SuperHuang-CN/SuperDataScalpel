package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.business.model.domain.DataModelStatus;
import cn.superhuang.data.scalpel.business.model.service.ModelPhysicalTableInspection;
import cn.superhuang.data.scalpel.business.model.service.ModelPhysicalTablePort;
import cn.superhuang.data.scalpel.business.model.service.PhysicalTableState;
import cn.superhuang.data.scalpel.dialect.api.DatabaseCapability;
import cn.superhuang.data.scalpel.dialect.api.DatabaseDialect;
import cn.superhuang.data.scalpel.dialect.api.DialectRegistry;
import cn.superhuang.data.scalpel.dialect.model.LogicalType;
import cn.superhuang.data.scalpel.dialect.query.InsertSelectQuery;
import cn.superhuang.data.scalpel.dialect.query.QueryColumn;
import cn.superhuang.data.scalpel.dialect.query.QueryInspection;
import cn.superhuang.data.scalpel.dialect.query.ReadOnlySelectQueryParser;
import cn.superhuang.data.scalpel.dialect.runtime.DatabaseAccessException;
import cn.superhuang.data.scalpel.dialect.runtime.JdbcQueryInspector;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** JDBC implementation: inspect models, compile the read-only query, and compare its output to the target model. */
@Component
public class JdbcLocalSqlDefinitionInspectionPort implements LocalSqlDefinitionInspectionPort {

    private final DialectRegistry dialectRegistry;
    private final JdbcQueryInspector queryInspector;
    private final ModelPhysicalTablePort physicalTablePort;

    public JdbcLocalSqlDefinitionInspectionPort(
            DialectRegistry dialectRegistry,
            JdbcQueryInspector queryInspector,
            ModelPhysicalTablePort physicalTablePort
    ) {
        this.dialectRegistry = dialectRegistry;
        this.queryInspector = queryInspector;
        this.physicalTablePort = physicalTablePort;
    }

    @Override
    public LocalSqlDefinitionInspection inspect(LocalSqlDefinitionInspectionRequest request) {
        List<LocalSqlDefinitionInspectionProblem> problems = new ArrayList<>();
        validateModels(request, problems);
        DatabaseDialect dialect = dialectRegistry.require(request.dataSource().getType().name());
        if (!dialect.definition().capabilities().contains(DatabaseCapability.INSERT_SELECT)) {
            problems.add(problem("WRITE_MODE_UNSUPPORTED", "当前数据库不支持 INSERT INTO ... SELECT", null));
        }
        if (request.writeMode() == cn.superhuang.data.scalpel.business.task.domain.LocalSqlWriteMode.OVERWRITE
                && !dialect.definition().capabilities().contains(DatabaseCapability.OVERWRITE_INSERT_SELECT)) {
            problems.add(problem("WRITE_MODE_UNSUPPORTED", "当前数据库第一阶段不支持 OVERWRITE，请使用 APPEND", null));
        }
        if (!problems.isEmpty()) {
            return new LocalSqlDefinitionInspection(problems, List.of(), List.of(), null);
        }

        InsertSelectQuery query;
        try {
            query = ReadOnlySelectQueryParser.parse(request.sql());
        } catch (IllegalArgumentException exception) {
            return new LocalSqlDefinitionInspection(List.of(problem("SQL_NOT_READ_ONLY", exception.getMessage(), null)), List.of(), List.of(), null);
        }
        QueryInspection inspection;
        try {
            inspection = queryInspector.inspect(
                    request.dataSource().getType().name(), request.dataSource().getConnection().toJdbcConnectionConfig(), query, request.timeout()
            );
        } catch (DatabaseAccessException exception) {
            return new LocalSqlDefinitionInspection(
                    List.of(problem("SQL_INSPECTION_FAILED", exception.getMessage(), null)), List.of(), List.of(), null
            );
        }
        return compareOutput(dialect, request, query, inspection);
    }

    private void validateModels(
            LocalSqlDefinitionInspectionRequest request,
            List<LocalSqlDefinitionInspectionProblem> problems
    ) {
        for (LocalSqlDefinitionInspectionRequest.ModelWithFields input : request.inputs()) {
            validateModel("输入模型", input, request, problems);
        }
        validateModel("输出模型", request.output(), request, problems);
    }

    private void validateModel(
            String role,
            LocalSqlDefinitionInspectionRequest.ModelWithFields subject,
            LocalSqlDefinitionInspectionRequest request,
            List<LocalSqlDefinitionInspectionProblem> problems
    ) {
        if (subject.model().getStatus() != DataModelStatus.PUBLISHED) {
            problems.add(problem("MODEL_NOT_PUBLISHED", role + "未发布：" + subject.model().getName(), null));
            return;
        }
        ModelPhysicalTableInspection physical = physicalTablePort.inspect(request.dataSource(), subject.model(), subject.fields());
        if (physical.state() != PhysicalTableState.MATCHED) {
            problems.add(problem("MODEL_PHYSICAL_TABLE_NOT_READY", role + "物理表未就绪：" + physical.message(), null));
        }
    }

    static LocalSqlDefinitionInspection compareOutput(
            DatabaseDialect dialect,
            LocalSqlDefinitionInspectionRequest request,
            InsertSelectQuery query,
            QueryInspection queryInspection
    ) {
        List<LocalSqlDefinitionInspectionProblem> problems = new ArrayList<>();
        Map<String, DataModelField> fieldsByCode = new HashMap<>();
        for (DataModelField field : request.output().fields()) {
            fieldsByCode.put(normalize(field.getCode()), field);
        }
        Set<String> observed = new HashSet<>();
        List<String> targetColumns = new ArrayList<>();
        List<LocalSqlDefinitionInspectionColumn> columns = new ArrayList<>();
        for (int index = 0; index < queryInspection.columns().size(); index++) {
            QueryColumn queryColumn = queryInspection.columns().get(index);
            String code = normalize(queryColumn.label());
            DataModelField target = fieldsByCode.get(code);
            if (!observed.add(code)) {
                problems.add(problem("DUPLICATE_OUTPUT_COLUMN", "查询结果列重复：" + queryColumn.label(), code));
            }
            if (target == null) {
                problems.add(problem("UNEXPECTED_OUTPUT_COLUMN", "查询结果包含输出模型未定义字段：" + queryColumn.label(), code));
            } else {
                targetColumns.add(target.getCode());
                if (!compatible(target.getFieldType(), queryColumn.logicalType())) {
                    problems.add(problem(
                            "OUTPUT_TYPE_INCOMPATIBLE",
                            "输出字段类型不兼容：" + target.getCode() + " 期望 " + target.getFieldType() + "，实际 " + queryColumn.logicalType(),
                            target.getCode()
                    ));
                }
            }
            columns.add(new LocalSqlDefinitionInspectionColumn(
                    index + 1, queryColumn.label(), queryColumn.logicalType(), queryColumn.nativeType(), queryColumn.nullable(),
                    target == null ? null : target.getCode()
            ));
        }
        List<DataModelField> primaryKeyFields = request.output().fields().stream()
                .filter(DataModelField::isPrimaryKey)
                .toList();
        if (primaryKeyFields.isEmpty()) {
            problems.add(problem("OUTPUT_MODEL_PRIMARY_KEY_REQUIRED", "输出模型必须定义主键字段", null));
        }
        for (DataModelField field : primaryKeyFields) {
            if (!observed.contains(normalize(field.getCode()))) {
                problems.add(problem(
                        "MISSING_PRIMARY_KEY_COLUMN",
                        "查询结果缺少输出模型主键字段：" + field.getCode(),
                        field.getCode()
                ));
            }
        }
        String generatedInsertSql = problems.isEmpty()
                ? dialect.renderInsertSelect(physicalTable(request, dialect), targetColumns, query)
                : null;
        return new LocalSqlDefinitionInspection(problems, columns, targetColumns, generatedInsertSql);
    }

    private static cn.superhuang.data.scalpel.dialect.model.TableIdentifier physicalTable(
            LocalSqlDefinitionInspectionRequest request,
            DatabaseDialect dialect
    ) {
        var config = request.dataSource().getConnection().toJdbcConnectionConfig();
        var model = request.output().model();
        return new cn.superhuang.data.scalpel.dialect.model.TableIdentifier(
                dialect.resolveCatalog(config, model.getCatalogName()),
                dialect.resolveSchema(config, model.getSchemaName()),
                model.getPhysicalTableName()
        );
    }

    private static boolean compatible(PlatformDataType target, LogicalType actual) {
        return switch (target) {
            case STRING -> actual == LogicalType.STRING;
            case BYTE, SHORT, INTEGER, LONG -> actual == LogicalType.INTEGER;
            case FLOAT, DOUBLE, DECIMAL -> actual == LogicalType.INTEGER || actual == LogicalType.DECIMAL;
            case BOOLEAN -> actual == LogicalType.BOOLEAN;
            case DATE -> actual == LogicalType.DATE;
            case TIMESTAMP, TIMESTAMP_NTZ -> actual == LogicalType.DATETIME;
            case BINARY -> actual == LogicalType.BINARY;
        };
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static LocalSqlDefinitionInspectionProblem problem(String code, String message, String columnCode) {
        return new LocalSqlDefinitionInspectionProblem(code, message, columnCode);
    }
}
