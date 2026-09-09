package cn.superhuang.data.scalpel.business.systemmcp;
import cn.superhuang.data.scalpel.business.systemmcp.service.*;
import cn.superhuang.data.scalpel.business.systemmcp.security.*;
import cn.superhuang.data.scalpel.business.systemmcp.config.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.JsonNode;
import java.util.*;
import java.nio.charset.StandardCharsets;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class SystemMcpProtocolTest {
    private final JsonMapper mapper=JsonMapper.builder().build();
    @Test void initializesListsFixedToolsAndHonorsNotifications() {
        var tools=mock(SystemMcpToolService.class);
        var service=new SystemMcpProtocolService(mapper,tools,new SystemMcpSchemaService(),mock(SystemMcpAuditService.class),new SystemMcpProperties());
        var identity=new SystemMcpAuthentication("reader",UUID.randomUUID(),"dssmcp_test",List.of());
        try {
            var init=service.handle(identity,bytes("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-06-18\",\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1\"}}}"),null);
            assertEquals("2025-06-18",mapper.valueToTree(init.getBody()).path("result").path("protocolVersion").asText());
            var list=mapper.valueToTree(service.handle(identity,bytes("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}"),null).getBody());
            assertEquals(3,list.path("result").path("tools").size());
            Set<String> names=new HashSet<>();
            for(JsonNode tool:list.path("result").path("tools"))names.add(tool.path("name").asText());
            assertEquals(Set.of("api_search","api_describe","api_invoke"),names);
            assertEquals(202,service.handle(identity,bytes("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"),null).getStatusCode().value());
            assertEquals(-32700,mapper.valueToTree(service.handle(identity,bytes("invalid"),null).getBody()).path("error").path("code").asInt());
            var call=mapper.valueToTree(service.handle(identity,bytes("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"api_describe\",\"arguments\":{\"operationIds\":[]}}}"),null).getBody());
            assertTrue(call.path("result").path("isError").asBoolean());
            verifyNoInteractions(tools);
        }
        finally {
            service.close();
        }
    }
    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
