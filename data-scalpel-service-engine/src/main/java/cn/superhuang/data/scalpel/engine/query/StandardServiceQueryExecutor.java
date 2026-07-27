package cn.superhuang.data.scalpel.engine.query;

import cn.superhuang.data.scalpel.contract.service.StandardServiceQueryRequest;
import cn.superhuang.data.scalpel.contract.service.ServiceQueryResponse;
import cn.superhuang.data.scalpel.engine.deployment.StoredServiceDeployment;
import cn.superhuang.data.scalpel.engine.datasource.EngineDataSourceStore;
import cn.superhuang.data.scalpel.dialect.api.DatabaseDialect;
import cn.superhuang.data.scalpel.dialect.api.DialectRegistry;
import cn.superhuang.data.scalpel.dialect.query.CompiledStandardQuery;
import cn.superhuang.data.scalpel.dialect.query.StandardQueryResult;
import cn.superhuang.data.scalpel.dialect.runtime.JdbcStandardQueryExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;

@Service
public class StandardServiceQueryExecutor {

    private final StandardServiceRequestCompiler requestCompiler;
    private final DialectRegistry dialectRegistry;
    private final DataSourcePoolRegistry poolRegistry;
    private final EngineDataSourceStore dataSourceStore;
    private final EngineQueryProperties properties;
    private final JdbcStandardQueryExecutor jdbcExecutor = new JdbcStandardQueryExecutor();

    public StandardServiceQueryExecutor(
            StandardServiceRequestCompiler requestCompiler,
            DialectRegistry dialectRegistry,
            DataSourcePoolRegistry poolRegistry,
            EngineDataSourceStore dataSourceStore,
            EngineQueryProperties properties
    ) {
        this.requestCompiler = requestCompiler;
        this.dialectRegistry = dialectRegistry;
        this.poolRegistry = poolRegistry;
        this.dataSourceStore = dataSourceStore;
        this.properties = properties;
    }

    public ServiceQueryResponse execute(
            StoredServiceDeployment deployment,
            StandardServiceQueryRequest request
    ) {
        CompiledServiceRequest compiledRequest = requestCompiler.compile(
                deployment.request().definition().standardDefinition(), request
        );
        var dataSource = dataSourceStore.requireSnapshot(deployment.request().dataSourceId());
        DatabaseDialect dialect;
        try {
            dialect = dialectRegistry.require(dataSource.databaseType());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "服务引擎不支持该数据库类型", exception);
        }
        CompiledStandardQuery query = dialect.compileStandardQuery(compiledRequest.query());
        try (Connection connection = poolRegistry.connection(dataSource)) {
            StandardQueryResult result = jdbcExecutor.execute(
                    connection, query, compiledRequest.pageSize(), Duration.ofSeconds(properties.timeoutSeconds())
            );
            return new ServiceQueryResponse(
                    compiledRequest.pageNo(), compiledRequest.pageSize(), result.totalCount(), result.rows()
            );
        } catch (SQLException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "数据源查询失败", exception);
        } catch (IllegalArgumentException exception) {
            throw new EngineQueryValidationException(exception.getMessage(), exception);
        }
    }
}
