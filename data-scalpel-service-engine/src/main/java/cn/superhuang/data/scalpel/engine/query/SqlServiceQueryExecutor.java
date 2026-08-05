package cn.superhuang.data.scalpel.engine.query;

import cn.superhuang.data.scalpel.contract.service.ServiceQueryResponse;
import cn.superhuang.data.scalpel.contract.service.SqlServiceDefinition;
import cn.superhuang.data.scalpel.contract.service.SqlServiceQueryRequest;
import cn.superhuang.data.scalpel.contract.service.SqlServiceResultFieldDefinition;
import cn.superhuang.data.scalpel.engine.deployment.StoredServiceDeployment;
import cn.superhuang.data.scalpel.engine.datasource.EngineApiStudioDataSourceService;
import cn.superhuang.data.scalpel.dialect.api.DatabaseCapability;
import cn.superhuang.data.scalpel.dialect.api.DatabaseDialect;
import cn.superhuang.data.scalpel.dialect.api.DialectRegistry;
import cn.superhuang.data.scalpel.dialect.model.TypeMappingResult;
import cn.superhuang.data.scalpel.dialect.query.QueryInspection;
import cn.superhuang.data.scalpel.dialect.query.SqlQueryResult;
import cn.superhuang.data.scalpel.dialect.runtime.JdbcSqlQueryExecutor;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;

@Service
public class SqlServiceQueryExecutor {

    private static final Logger log = LoggerFactory.getLogger(SqlServiceQueryExecutor.class);

    private final SqlServiceRequestCompiler requestCompiler;
    private final DialectRegistry dialectRegistry;
    private final EngineApiStudioDataSourceService dataSourceService;
    private final EngineQueryProperties properties;
    private final JdbcSqlQueryExecutor jdbcExecutor = new JdbcSqlQueryExecutor();

    public SqlServiceQueryExecutor(
            SqlServiceRequestCompiler requestCompiler,
            DialectRegistry dialectRegistry,
            EngineApiStudioDataSourceService dataSourceService,
            EngineQueryProperties properties
    ) {
        this.requestCompiler = requestCompiler;
        this.dialectRegistry = dialectRegistry;
        this.dataSourceService = dataSourceService;
        this.properties = properties;
    }

    public ServiceQueryResponse execute(StoredServiceDeployment deployment, SqlServiceQueryRequest request) {
        SqlServiceDefinition definition = deployment.request().definition().sqlDefinition();
        CompiledSqlServiceRequest compiled = requestCompiler.compile(definition, request);
        var dataSource = dataSourceService.resolve(deployment.request().dataSourceId());
        DatabaseDialect dialect;
        try {
            dialect = dialectRegistry.require(dataSource.databaseType());
            if (!dialect.definition().capabilities().contains(DatabaseCapability.SQL_SERVICE_QUERY)) {
                throw new IllegalArgumentException("Database type does not support SQL services");
            }
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "服务引擎不支持该 SQL 服务数据库类型", exception);
        }
        var query = dialect.compileSqlServiceQuery(
                definition.jdbcSql(), compiled.parameters(), compiled.offset(), compiled.pageSize(), compiled.returnCount()
        );
        try (Connection connection = dataSourceService.connection(dataSource)) {
            SqlQueryResult result = jdbcExecutor.execute(
                    connection, dialect, query, compiled.pageSize(), Duration.ofSeconds(properties.timeoutSeconds())
            );
            requireOutputSnapshot(dialect, definition.resultFields(), result.inspection());
            return new ServiceQueryResponse(
                    compiled.pageNo(), compiled.pageSize(), result.totalCount(), result.rows()
            );
        } catch (SQLException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "SQL 服务数据源查询失败", exception);
        } catch (IllegalArgumentException exception) {
            throw new EngineQueryValidationException(exception.getMessage(), exception);
        }
    }

    private static void requireOutputSnapshot(
            DatabaseDialect dialect,
            List<SqlServiceResultFieldDefinition> expected,
            QueryInspection actual
    ) {
        if (expected.size() != actual.columns().size()) {
            log.warn("SQL service output column count drifted: expected={}, actual={}",
                    expected.size(), actual.columns().size());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "SQL 服务输出结构已发生变化");
        }
        for (int index = 0; index < expected.size(); index++) {
            SqlServiceResultFieldDefinition expectedField = expected.get(index);
            var actualColumn = actual.columns().get(index);
            TypeMappingResult<cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition> mapping =
                    dialect.mapToPlatformType(actualColumn.jdbcTypeDescriptor());
            if (!expectedField.name().equals(actualColumn.label())
                    || !mapping.acceptable()
                    || !expectedField.typeDefinition().equals(mapping.definition())
                    || expectedField.nullable() != actualColumn.nullable()) {
                log.warn("SQL service output column drifted at index {}: expected={}, actual={}, mapping={}",
                        index, expectedField, actualColumn, mapping);
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "SQL 服务输出结构已发生变化");
            }
        }
    }
}
