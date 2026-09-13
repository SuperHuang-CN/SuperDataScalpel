package cn.superhuang.data.scalpel.admin.config;

import cn.superhuang.data.scalpel.business.datasource.web.request.CreateDataSourceRequest;
import cn.superhuang.data.scalpel.business.systemmcp.service.SystemMcpSchemaService;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import org.junit.jupiter.api.Test;
import org.springdoc.core.converters.PolymorphicModelConverter;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.*;

public class DataSourceOpenApiSchemaTest {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final SystemMcpSchemaService validator = new SystemMcpSchemaService();

    private ObjectNode schema() throws Exception {
        var converters = new ModelConverters(true);
        converters.addConverter(new PolymorphicModelConverter(new ObjectMapperProvider(new SpringDocConfigProperties())));
        converters.addConverter(new OpenApiConfiguration().jsonClassDescriptionModelConverter());
        var model = converters.resolveAsResolvedSchema(new AnnotatedType(CreateDataSourceRequest.class));
        var root = (ObjectNode) mapper.readTree(io.swagger.v3.core.util.Json31.mapper().writeValueAsString(model.schema));
        root.putObject("components").set("schemas", mapper.readTree(
                io.swagger.v3.core.util.Json31.mapper().writeValueAsString(model.referencedSchemas)));
        return root;
    }

    @Test
    void jdbcCreationValidatesAndRejectsInvalidDiscriminators() throws Exception {
        var schema = schema();
        var input = (ObjectNode) mapper.readTree("""
                {"code":"pg_home_test","name":"PG-HOME-TEST","type":"POSTGRESQL","purposes":["SOURCE"],
                 "connection":{"kind":"JDBC","host":"example.invalid","port":5432,"databaseName":"test",
                 "schemaName":"public","username":"test","password":"test-only"}}
                """);
        validator.validate(schema, input);
        var connection = (ObjectNode) input.get("connection");
        connection.put("kind", "UNKNOWN");
        assertThrows(ResponseStatusException.class, () -> validator.validate(schema, input));
        connection.put("kind", "KAFKA");
        assertThrows(ResponseStatusException.class, () -> validator.validate(schema, input));
        connection.put("kind", "JDBC");
        connection.remove("host");
        assertThrows(ResponseStatusException.class, () -> validator.validate(schema, input));
    }

    @Test
    void nestedHttpAuthenticationSelectsExactlyOneBranch() throws Exception {
        var schema = schema();
        var input = (ObjectNode) mapper.readTree("""
                {"code":"http_test","name":"HTTP","type":"HTTP_API","purposes":["SOURCE"],
                 "connection":{"kind":"HTTP_API","baseUrl":"https://example.invalid",
                 "authentication":{"type":"NONE"}}}
                """);
        validator.validate(schema, input);
        var auth = (ObjectNode) input.path("connection").path("authentication");
        auth.put("type", "BASIC").put("username", "test").put("password", "test-only");
        validator.validate(schema, input);
        auth.put("type", "UNKNOWN");
        assertThrows(ResponseStatusException.class, () -> validator.validate(schema, input));
    }
}
