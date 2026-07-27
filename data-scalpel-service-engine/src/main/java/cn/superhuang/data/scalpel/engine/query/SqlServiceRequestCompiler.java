package cn.superhuang.data.scalpel.engine.query;

import cn.superhuang.data.scalpel.contract.service.SqlServiceDefinition;
import cn.superhuang.data.scalpel.contract.service.SqlServiceParameterDefinition;
import cn.superhuang.data.scalpel.contract.service.SqlServiceQueryRequest;
import cn.superhuang.data.scalpel.dialect.query.PlatformQueryValueConverter;
import cn.superhuang.data.scalpel.dialect.query.SqlQueryParameter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Validates a public SQL service request and produces ordered JDBC bindings. */
@Component
public class SqlServiceRequestCompiler {

    private final EngineQueryProperties properties;

    public SqlServiceRequestCompiler(EngineQueryProperties properties) {
        this.properties = properties;
    }

    CompiledSqlServiceRequest compile(SqlServiceDefinition definition, SqlServiceQueryRequest request) {
        try {
            int pageNo = request.pageNo() == null ? 1 : request.pageNo();
            int pageSize = request.pageSize() == null ? properties.defaultPageSize() : request.pageSize();
            if (pageNo < 1) throw new IllegalArgumentException("pageNo must be at least 1");
            if (pageSize < 1 || pageSize > properties.maximumPageSize()) {
                throw new IllegalArgumentException("pageSize must be between 1 and " + properties.maximumPageSize());
            }
            long offsetLong = Math.multiplyExact((long) pageNo - 1L, pageSize);
            if (offsetLong > Integer.MAX_VALUE || offsetLong > properties.maximumOffset()) {
                throw new IllegalArgumentException("Query offset exceeds maximum " + properties.maximumOffset());
            }
            Map<String, SqlServiceParameterDefinition> definitions = new LinkedHashMap<>();
            definition.parameters().forEach(parameter -> definitions.put(parameter.name(), parameter));
            for (String name : request.arguments().keySet()) {
                if (!definitions.containsKey(name)) throw new IllegalArgumentException("Unknown argument: " + name);
            }
            Map<String, Object> values = request.arguments();
            for (SqlServiceParameterDefinition parameter : definition.parameters()) {
                if (parameter.required() && (!values.containsKey(parameter.name()) || values.get(parameter.name()) == null)) {
                    throw new IllegalArgumentException("Required argument is missing: " + parameter.name());
                }
            }
            List<SqlQueryParameter> parameters = new ArrayList<>(definition.bindingOrder().size());
            for (String name : definition.bindingOrder()) {
                SqlServiceParameterDefinition parameter = definitions.get(name);
                Object value = values.containsKey(name)
                        ? PlatformQueryValueConverter.convert(values.get(name), parameter.typeDefinition())
                        : null;
                parameters.add(new SqlQueryParameter(value, parameter.typeDefinition()));
            }
            return new CompiledSqlServiceRequest(
                    pageNo, pageSize, Math.toIntExact(offsetLong), Boolean.TRUE.equals(request.returnCount()), parameters
            );
        } catch (IllegalArgumentException exception) {
            throw new EngineQueryValidationException(exception.getMessage(), exception);
        }
    }
}
