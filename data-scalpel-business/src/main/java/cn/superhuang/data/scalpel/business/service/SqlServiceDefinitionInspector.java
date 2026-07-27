package cn.superhuang.data.scalpel.business.service;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.service.web.response.SqlServiceTestProblem;
import cn.superhuang.data.scalpel.business.service.web.response.SqlServiceTestResponse;
import cn.superhuang.data.scalpel.contract.service.ServiceQueryResponse;
import cn.superhuang.data.scalpel.contract.service.SqlServiceParameterDefinition;
import cn.superhuang.data.scalpel.contract.service.SqlServiceResultFieldDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.dialect.api.DatabaseCapability;
import cn.superhuang.data.scalpel.dialect.api.DatabaseDialect;
import cn.superhuang.data.scalpel.dialect.api.DialectRegistry;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionFactory;
import cn.superhuang.data.scalpel.dialect.model.TypeMappingResult;
import cn.superhuang.data.scalpel.dialect.query.CompiledSqlServiceQuery;
import cn.superhuang.data.scalpel.dialect.query.NamedParameterSqlCompiler;
import cn.superhuang.data.scalpel.dialect.query.PlatformQueryValueConverter;
import cn.superhuang.data.scalpel.dialect.query.QueryColumn;
import cn.superhuang.data.scalpel.dialect.query.QueryInspection;
import cn.superhuang.data.scalpel.dialect.query.ReadOnlySelectQueryParser;
import cn.superhuang.data.scalpel.dialect.query.SqlQueryParameter;
import cn.superhuang.data.scalpel.dialect.query.SqlQueryResult;
import cn.superhuang.data.scalpel.dialect.query.SqlTemplateCompilation;
import cn.superhuang.data.scalpel.dialect.runtime.DatabaseAccessException;
import cn.superhuang.data.scalpel.dialect.runtime.JdbcQueryInspector;
import cn.superhuang.data.scalpel.dialect.runtime.JdbcSqlQueryExecutor;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Compiles, inspects and previews SQL definitions without opening a management-database transaction. */
@Component
public class SqlServiceDefinitionInspector {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private final DialectRegistry dialectRegistry;
    private final JdbcConnectionFactory connectionFactory;
    private final JdbcQueryInspector queryInspector;
    private final JdbcSqlQueryExecutor queryExecutor = new JdbcSqlQueryExecutor();

    public SqlServiceDefinitionInspector(
            DialectRegistry dialectRegistry,
            JdbcConnectionFactory connectionFactory,
            JdbcQueryInspector queryInspector
    ) {
        this.dialectRegistry = dialectRegistry;
        this.connectionFactory = connectionFactory;
        this.queryInspector = queryInspector;
    }

    public SqlServiceInspection inspect(
            DataSource dataSource,
            String sqlText,
            List<SqlServiceParameterDefinition> parameters
    ) {
        PreparedDefinition prepared;
        try {
            prepared = prepare(dataSource, sqlText, parameters, null, false);
        } catch (InspectionFailure failure) {
            return invalid(failure.code, failure.getMessage(), failure.subject);
        }
        try {
            CompiledSqlServiceQuery runtimeQuery = prepared.dialect.compileSqlServiceQuery(
                    prepared.compilation.jdbcSql(), prepared.bindings, 0, 1, false
            );
            QueryInspection queryInspection = queryInspector.inspectPrepared(
                    dataSource.getType().name(),
                    dataSource.getConnection().toJdbcConnectionConfig(),
                    ReadOnlySelectQueryParser.parse(runtimeQuery.dataQuery().sql()),
                    runtimeQuery.dataQuery().parameters(),
                    TIMEOUT
            );
            FieldsResult fields = fields(prepared.dialect, queryInspection);
            return new SqlServiceInspection(
                    prepared.compilation.jdbcSql(), prepared.compilation.bindingOrder(), fields.fields, fields.problems
            );
        } catch (DatabaseAccessException exception) {
            return invalid(exception.code(), exception.getMessage(), null);
        } catch (RuntimeException exception) {
            return invalid("SQL_INSPECTION_FAILED", exception.getMessage(), null);
        }
    }

    public List<SqlServiceTestProblem> validateLocally(
            DataSource dataSource,
            String sqlText,
            List<SqlServiceParameterDefinition> parameters
    ) {
        try {
            prepare(dataSource, sqlText, parameters, null, false);
            return List.of();
        } catch (InspectionFailure failure) {
            return List.of(problem(failure.code, failure.getMessage(), failure.subject));
        }
    }

    public SqlServiceTestResponse test(
            DataSource dataSource,
            String sqlText,
            List<SqlServiceParameterDefinition> parameters,
            Map<String, Object> arguments,
            int previewSize
    ) {
        long started = System.nanoTime();
        PreparedDefinition prepared;
        try {
            prepared = prepare(dataSource, sqlText, parameters, arguments, true);
            CompiledSqlServiceQuery query = prepared.dialect.compileSqlServiceQuery(
                    prepared.compilation.jdbcSql(), prepared.bindings, 0, previewSize, false
            );
            try (Connection connection = connectionFactory.open(
                    prepared.dialect.createConnectionSpec(dataSource.getConnection().toJdbcConnectionConfig())
            )) {
                try {
                    connection.setReadOnly(true);
                } catch (java.sql.SQLException ignored) {
                    // Database credentials remain the final read-only boundary.
                }
                SqlQueryResult result = queryExecutor.execute(connection, prepared.dialect, query, previewSize, TIMEOUT);
                FieldsResult fields = fields(prepared.dialect, result.inspection());
                if (!fields.problems.isEmpty()) {
                    return response(false, fields.problems, fields.fields, null, started);
                }
                return response(
                        true,
                        List.of(),
                        fields.fields,
                        new ServiceQueryResponse(1, previewSize, null, result.rows()),
                        started
                );
            }
        } catch (InspectionFailure failure) {
            return response(false, List.of(problem(failure.code, failure.getMessage(), failure.subject)), List.of(), null, started);
        } catch (Exception exception) {
            return response(false, List.of(problem("SQL_EXECUTION_FAILED", safeMessage(exception), null)), List.of(), null, started);
        }
    }

    private PreparedDefinition prepare(
            DataSource dataSource,
            String sqlText,
            List<SqlServiceParameterDefinition> parameters,
            Map<String, Object> arguments,
            boolean requireArguments
    ) {
        DatabaseDialect dialect;
        try {
            dialect = dialectRegistry.require(dataSource.getType().name());
        } catch (IllegalArgumentException exception) {
            throw failure("DATABASE_TYPE_UNSUPPORTED", "不支持该数据库类型", null);
        }
        if (!dialect.definition().capabilities().contains(DatabaseCapability.SQL_SERVICE_QUERY)) {
            throw failure("SQL_SERVICE_DATABASE_UNSUPPORTED", "当前数据库类型未开放 SQL 服务能力", null);
        }
        try {
            ReadOnlySelectQueryParser.parseServiceQuery(sqlText);
        } catch (IllegalArgumentException exception) {
            throw failure("SQL_NOT_READ_ONLY", exception.getMessage(), null);
        }
        SqlTemplateCompilation compilation;
        try {
            compilation = NamedParameterSqlCompiler.compile(sqlText);
        } catch (IllegalArgumentException exception) {
            throw failure("SQL_PARAMETER_SYNTAX_INVALID", exception.getMessage(), null);
        }
        Map<String, SqlServiceParameterDefinition> byName = new LinkedHashMap<>();
        for (SqlServiceParameterDefinition parameter : parameters) {
            if (byName.putIfAbsent(parameter.name(), parameter) != null) {
                throw failure("DUPLICATE_PARAMETER", "参数重复：" + parameter.name(), parameter.name());
            }
        }
        Set<String> used = new HashSet<>(compilation.bindingOrder());
        for (String name : used) {
            if (!byName.containsKey(name)) {
                throw failure("UNDECLARED_PARAMETER", "SQL 使用了未声明参数：" + name, name);
            }
        }
        for (String name : byName.keySet()) {
            if (!used.contains(name)) {
                throw failure("UNUSED_PARAMETER", "参数未在 SQL 中使用：" + name, name);
            }
        }
        Map<String, Object> values = arguments == null ? Map.of() : arguments;
        for (String name : values.keySet()) {
            if (!byName.containsKey(name)) {
                throw failure("UNKNOWN_ARGUMENT", "请求包含未知参数：" + name, name);
            }
        }
        if (requireArguments) {
            for (SqlServiceParameterDefinition parameter : parameters) {
                if (parameter.required() && (!values.containsKey(parameter.name()) || values.get(parameter.name()) == null)) {
                    throw failure("REQUIRED_ARGUMENT_MISSING", "缺少必填参数：" + parameter.name(), parameter.name());
                }
            }
        }
        List<SqlQueryParameter> bindings = new ArrayList<>(compilation.bindingOrder().size());
        for (String name : compilation.bindingOrder()) {
            SqlServiceParameterDefinition parameter = byName.get(name);
            Object value = null;
            if (requireArguments && values.containsKey(name)) {
                try {
                    value = PlatformQueryValueConverter.convert(values.get(name), parameter.typeDefinition());
                } catch (IllegalArgumentException exception) {
                    throw failure("ARGUMENT_TYPE_INVALID", "参数 " + name + "：" + exception.getMessage(), name);
                }
            }
            bindings.add(new SqlQueryParameter(value, parameter.typeDefinition()));
        }
        return new PreparedDefinition(dialect, compilation, bindings);
    }

    private static FieldsResult fields(DatabaseDialect dialect, QueryInspection inspection) {
        List<SqlServiceResultFieldDefinition> fields = new ArrayList<>();
        List<SqlServiceTestProblem> problems = new ArrayList<>();
        Set<String> names = new HashSet<>();
        if (inspection.columns().size() > 200) {
            problems.add(problem("TOO_MANY_OUTPUT_COLUMNS", "SQL 输出列不能超过 200 个", null));
        }
        for (QueryColumn column : inspection.columns()) {
            String normalized = column.label().toLowerCase(Locale.ROOT);
            if (!names.add(normalized)) {
                problems.add(problem("DUPLICATE_OUTPUT_COLUMN", "SQL 输出列重复：" + column.label(), column.label()));
                continue;
            }
            TypeMappingResult<cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition> mapping =
                    dialect.mapToPlatformType(column.jdbcTypeDescriptor());
            if (!mapping.acceptable() || mapping.definition() == null) {
                problems.add(problem(
                        "OUTPUT_TYPE_UNSUPPORTED",
                        "输出列 " + column.label() + " 类型不受支持：" + safe(mapping.message(), column.nativeType()),
                        column.label()
                ));
                continue;
            }
            if (mapping.definition().type() == PlatformDataType.BINARY) {
                problems.add(problem("OUTPUT_TYPE_UNSUPPORTED", "输出列不支持 BINARY：" + column.label(), column.label()));
                continue;
            }
            if (mapping.definition().type() == PlatformDataType.GEOMETRY) {
                problems.add(problem(
                        "SPATIAL_FIELD_UNSUPPORTED",
                        "输出列不支持 Geometry：" + column.label(),
                        column.label()
                ));
                continue;
            }
            fields.add(new SqlServiceResultFieldDefinition(column.label(), mapping.definition(), column.nullable()));
        }
        return new FieldsResult(fields, problems);
    }

    private static SqlServiceInspection invalid(String code, String message, String subject) {
        return new SqlServiceInspection(null, List.of(), List.of(), List.of(problem(code, message, subject)));
    }

    private static SqlServiceTestResponse response(
            boolean valid,
            List<SqlServiceTestProblem> problems,
            List<SqlServiceResultFieldDefinition> fields,
            ServiceQueryResponse preview,
            long started
    ) {
        return new SqlServiceTestResponse(valid, problems, fields, preview, elapsedMillis(started));
    }

    private static long elapsedMillis(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000L);
    }

    private static SqlServiceTestProblem problem(String code, String message, String subject) {
        return new SqlServiceTestProblem(code, safe(message, "SQL 服务检查失败"), subject);
    }

    private static InspectionFailure failure(String code, String message, String subject) {
        return new InspectionFailure(code, safe(message, code), subject);
    }

    private static String safeMessage(Exception exception) {
        Throwable current = exception;
        while (current.getCause() != null) current = current.getCause();
        return safe(current.getMessage(), "SQL 查询执行失败");
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record PreparedDefinition(
            DatabaseDialect dialect,
            SqlTemplateCompilation compilation,
            List<SqlQueryParameter> bindings
    ) {
    }

    private record FieldsResult(
            List<SqlServiceResultFieldDefinition> fields,
            List<SqlServiceTestProblem> problems
    ) {
    }

    private static final class InspectionFailure extends RuntimeException {
        private final String code;
        private final String subject;

        private InspectionFailure(String code, String message, String subject) {
            super(message);
            this.code = code;
            this.subject = subject;
        }
    }
}
