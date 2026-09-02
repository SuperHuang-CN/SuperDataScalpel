package cn.superhuang.data.scalpel.engine.deployment;

import cn.superhuang.data.scalpel.contract.service.DataServiceType;
import cn.superhuang.data.scalpel.contract.service.ServiceDeploymentRequest;
import cn.superhuang.data.scalpel.contract.service.SqlServiceDefinition;
import cn.superhuang.data.scalpel.dialect.api.DatabaseCapability;
import cn.superhuang.data.scalpel.dialect.api.DatabaseDialect;
import cn.superhuang.data.scalpel.dialect.api.DialectRegistry;
import cn.superhuang.data.scalpel.dialect.query.ReadOnlySelectQueryParser;
import cn.superhuang.data.scalpel.dialect.query.NamedParameterSqlCompiler;
import cn.superhuang.data.scalpel.engine.datasource.EngineApiStudioDataSourceService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.Set;

/** Re-validates an immutable control-plane snapshot before it can become a live Engine route. */
@Component
public class EngineDeploymentValidator {

    private final EngineApiStudioDataSourceService dataSourceService;
    private final DialectRegistry dialectRegistry;

    public EngineDeploymentValidator(
            EngineApiStudioDataSourceService dataSourceService,
            DialectRegistry dialectRegistry
    ) {
        this.dataSourceService = dataSourceService;
        this.dialectRegistry = dialectRegistry;
    }

    public void validate(ServiceDeploymentRequest request) {
        if (request.definition().type() == DataServiceType.SPATIAL_SERVICE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "SPATIAL_SERVICE 由 GeoServer 承载，不能部署到 DataScalpel Service Engine"
            );
        }
        var dataSource = dataSourceService.resolve(request.dataSourceId());
        if (request.definition().type() != DataServiceType.SQL_QUERY) {
            return;
        }
        SqlServiceDefinition definition = request.definition().sqlDefinition();
        try {
            DatabaseDialect dialect = dialectRegistry.require(dataSource.databaseType());
            if (!dialect.definition().capabilities().contains(DatabaseCapability.SQL_SERVICE_QUERY)) {
                throw new IllegalArgumentException("Database type does not support SQL services");
            }
            ReadOnlySelectQueryParser.parseServiceQuery(definition.jdbcSql());
            requireBindingOrder(definition);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SQL 服务部署快照无效：" + exception.getMessage(), exception);
        }
    }

    private static void requireBindingOrder(SqlServiceDefinition definition) {
        Set<String> declared = new HashSet<>();
        definition.parameters().forEach(parameter -> declared.add(parameter.name()));
        Set<String> used = new HashSet<>(definition.bindingOrder());
        if (!declared.equals(used)) {
            throw new IllegalArgumentException("SQL parameter declarations do not match binding order");
        }
        int placeholders = NamedParameterSqlCompiler.countJdbcPlaceholders(definition.jdbcSql());
        if (placeholders != definition.bindingOrder().size()) {
            throw new IllegalArgumentException("SQL placeholder count does not match binding order");
        }
    }
}
