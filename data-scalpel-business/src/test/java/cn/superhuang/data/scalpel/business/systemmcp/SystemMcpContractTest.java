package cn.superhuang.data.scalpel.business.systemmcp;
import cn.superhuang.data.scalpel.business.systemmcp.service.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
class SystemMcpContractTest {
    private final JsonMapper mapper=JsonMapper.builder().build();
    private final SystemMcpSchemaService schemas=new SystemMcpSchemaService();
    private final SystemMcpContractBuilder builder=new SystemMcpContractBuilder(mapper,schemas);
    @Test void retainsRecursiveReferencesAndValidatesArraysAndNullable() {
        var doc=mapper.readTree("""
  {"components":{"schemas":{"Node":{"type":"object","required":["name"],"properties":{"name":{"type":"string"},"child":{"$ref":"#/components/schemas/Node","nullable":true}}},"Unused":{"type":"string"}}}}
  """);
        var op=mapper.readTree("""
  {"parameters":[{"in":"path","name":"id","required":true,"schema":{"type":"integer"}},{"in":"query","name":"sort","schema":{"type":"array","items":{"type":"string"}}}],"requestBody":{"required":true,"content":{"application/json":{"schema":{"type":"array","items":{"$ref":"#/components/schemas/Node"}}}}},"responses":{"204":{"description":"empty"}}}
  """);
        var contract=builder.build(doc,mapper.createObjectNode(),op);
        assertTrue(contract.path("components").path("schemas").has("Node"));
        assertFalse(contract.path("components").path("schemas").has("Unused"));
        schemas.validate(contract.path("inputSchema"),mapper.readTree("{\"pathParams\":{\"id\":1},\"queryParams\":{\"sort\":[\"name\",\"-createdAt\"]},\"body\":[{\"name\":\"测试\",\"child\":null}]}"));
        assertThrows(ResponseStatusException.class,()->schemas.validate(contract.path("inputSchema"),mapper.readTree("{\"pathParams\":{\"id\":1},\"body\":[{}]}")));
        assertThrows(ResponseStatusException.class,()->schemas.validate(contract.path("inputSchema"),mapper.readTree("{\"pathParams\":{\"id\":1},\"body\":[],\"queryParams\":{\"unknown\":1}}")));
    }
    @Test void rejectsRemoteReferencesAndHeaderParameters() {
        assertThrows(IllegalArgumentException.class,()->schemas.check(mapper.readTree("{\"$ref\":\"https://example.com/schema\"}")));
        assertThrows(IllegalArgumentException.class,()->builder.build(mapper.createObjectNode(),mapper.readTree("{\"parameters\":[{\"in\":\"header\",\"name\":\"secret\",\"schema\":{\"type\":\"string\"}}]}"),mapper.createObjectNode()));
    }
    @Test void preservesScalarJsonBody() {
        var c=builder.build(mapper.createObjectNode(),mapper.createObjectNode(),mapper.readTree("{\"requestBody\":{\"required\":true,\"content\":{\"application/json\":{\"schema\":{\"type\":\"integer\",\"minimum\":1}}}},\"responses\":{}}"));
        schemas.validate(c.path("inputSchema"),mapper.readTree("{\"body\":4}"));
        assertThrows(ResponseStatusException.class,()->schemas.validate(c.path("inputSchema"),mapper.readTree("{\"body\":0}")));
    }
    @Test void rejectsPolymorphicInheritanceCycleBeforeValidation() {
        var schema = mapper.readTree("""
            {"$ref":"#/$defs/Base","$defs":{
              "Base":{"oneOf":[{"$ref":"#/$defs/Child"}]},
              "Child":{"allOf":[{"$ref":"#/$defs/Base"},{"type":"object"}]}
            }}
            """);
        var failure = assertThrows(IllegalArgumentException.class, () -> schemas.check(schema));
        assertTrue(failure.getMessage().contains("循环引用"));
    }
    @Test void rejectsFileResponseBehindLocalReference() {
        var doc = mapper.readTree("{\"components\":{\"responses\":{\"Download\":{\"description\":\"file\",\"content\":{\"application/octet-stream\":{\"schema\":{\"type\":\"string\",\"format\":\"binary\"}}}}}}}");
        var operation = mapper.readTree("{\"responses\":{\"200\":{\"$ref\":\"#/components/responses/Download\"}}}");
        assertThrows(IllegalArgumentException.class, () -> builder.build(doc, mapper.createObjectNode(), operation));
    }

    @Test void preservesBusinessFieldsNamedAfterSchemaKeywords() {
        var doc = mapper.readTree("""
            {"components":{"schemas":{"Column":{"type":"object","properties":{
              "nullable":{"type":"boolean"},
              "format":{"type":"string"},
              "$ref":{"type":"string"},
              "example":{"type":"string","nullable":true},
              "enum":{"type":"number","minimum":0,"exclusiveMinimum":true},
              "allOf":{"type":"string"}
            },"example":{"nullable":true,"format":"binary"}}}}}
            """);
        var operation = mapper.readTree("""
            {"requestBody":{"content":{"application/json":{"schema":{"$ref":"#/components/schemas/Column"}}}},
             "responses":{"200":{"content":{"application/json":{"schema":{"type":"array","items":{"$ref":"#/components/schemas/Column"}}}}}}}
            """);
        var contract = builder.build(doc, mapper.createObjectNode(), operation);
        var column = contract.path("components").path("schemas").path("Column");
        assertEquals("boolean", column.path("properties").path("nullable").path("type").asText());
        assertTrue(column.path("properties").path("example").has("anyOf"));
        assertEquals(doc.path("components").path("schemas").path("Column").path("example"), column.path("example"));
        schemas.validate(contract.path("inputSchema"), mapper.readTree("""
            {"body":{"nullable":false,"format":"text","$ref":"business value","example":null,"enum":1,"allOf":"value"}}
            """));
        assertThrows(ResponseStatusException.class, () -> schemas.validate(contract.path("inputSchema"),
                mapper.readTree("{\"body\":{\"nullable\":\"wrong type\"}}")));
        assertThrows(ResponseStatusException.class, () -> schemas.validate(contract.path("inputSchema"),
                mapper.readTree("{\"body\":{\"enum\":0}}")));
    }

    @Test void rejectsBinarySchemaEvenWhenBusinessFieldIsNamedExample() {
        var operation = mapper.readTree("""
            {"responses":{"200":{"content":{"application/json":{"schema":{"type":"object",
              "properties":{"example":{"type":"string","format":"binary"}}}}}}}}
            """);
        assertThrows(IllegalArgumentException.class, () -> builder.build(mapper.createObjectNode(), mapper.createObjectNode(), operation));
    }

}
