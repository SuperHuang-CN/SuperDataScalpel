package cn.superhuang.data.scalpel.business.systemmcp.service;
import cn.superhuang.data.scalpel.business.systemmcp.security.SystemMcpAuthentication;
import cn.superhuang.data.scalpel.business.systemmcp.config.SystemMcpProperties;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.json.schema.JsonSchemaValidator.ValidationResponse;
import io.modelcontextprotocol.server.*;
import io.modelcontextprotocol.spec.*;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.http.ResponseEntity;
import jakarta.annotation.PreDestroy;
import reactor.core.publisher.Mono;
import tools.jackson.databind.*;
import java.util.*;
@Service
public class SystemMcpProtocolService {
    public static final List<String> VERSIONS=List.of("2024-11-05","2025-03-26","2025-06-18");
    private final ObjectMapper mapper;
    private final McpJsonMapper sdk=McpJsonMapper.createDefault();
    private final Transport transport=new Transport();
    private final McpStatelessSyncServer server;
    private final SystemMcpProperties properties;
    public SystemMcpProtocolService(ObjectMapper mapper,SystemMcpToolService tools,SystemMcpSchemaService schemas,SystemMcpAuditService audits,SystemMcpProperties p) {
        this.mapper=mapper;
        properties=p;
        var builder=McpServer.sync(transport).jsonMapper(sdk).immediateExecution(true).serverInfo("DataScalpel System MCP","1.0").capabilities(McpSchema.ServerCapabilities.builder().tools(false).build()).requestTimeout(p.getInvokeTimeout().plusSeconds(2)).jsonSchemaValidator((s,c)->ValidationResponse.asValid(null));
        Map<String,String> definitions=Map.of(
        "api_search","{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"},\"module\":{\"type\":\"string\"},\"effect\":{\"type\":\"string\",\"enum\":[\"READ\",\"WRITE\",\"EXECUTE\"]},\"offset\":{\"type\":\"integer\",\"minimum\":0},\"limit\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":50}},\"additionalProperties\":false}",
        "api_describe","{\"type\":\"object\",\"properties\":{\"operationIds\":{\"type\":\"array\",\"items\":{\"type\":\"string\"},\"minItems\":1,\"maxItems\":5}},\"required\":[\"operationIds\"],\"additionalProperties\":false}",
        "api_invoke","{\"type\":\"object\",\"properties\":{\"operationId\":{\"type\":\"string\"},\"pathParams\":{\"type\":\"object\"},\"queryParams\":{\"type\":\"object\"},\"body\":{}},\"required\":[\"operationId\"],\"additionalProperties\":false}");
        for(String name:List.of("api_search","api_describe","api_invoke")) {
            JsonNode schema=mapper.readTree(definitions.get(name));
            Map<String,Object> props=mapper.convertValue(schema.path("properties"),Map.class);
            List<String> required=new ArrayList<>();
            schema.path("required").forEach(x->required.add(x.asText()));
            var descriptor=McpSchema.Tool.builder().name(name).description(switch(name) {
                case "api_search"->"检索当前用户可访问的已开放系统接口";case "api_describe"->"读取最新接口契约、前置条件及操作性质";default->"依据最新契约执行系统接口；结果不确定时先查询业务状态，不要重复提交";
            }).inputSchema(new McpSchema.JsonSchema("object",props,required,false,null,null)).annotations(new McpSchema.ToolAnnotations(name,!name.equals("api_invoke"),name.equals("api_invoke"),!name.equals("api_invoke"),false,null)).build();
            builder.toolCall(descriptor,(context,request)-> {
                var identity=(SystemMcpAuthentication)context.get("identity");long start=System.nanoTime();Object value;boolean failed=false;String code=null;String operation=null;Integer businessStatus=null;String executionStatus=null;
                try {
                    JsonNode args=mapper.valueToTree(request.arguments()==null?Map.of():request.arguments());schemas.validate(schema,args);operation=args.path("operationId").asText(null);value=tools.call(name,identity,args);JsonNode result=mapper.valueToTree(value);failed=result.path("code").isTextual()||result.path("httpStatus").asInt(200)>=300;code=result.path("code").asText(null);businessStatus=result.hasNonNull("httpStatus")?result.path("httpStatus").asInt():null;executionStatus=result.path("executionStatus").asText(null);
                }
                catch(ResponseStatusException e) {
                    failed=true;code=e.getBody().getProperties()!=null&&e.getBody().getProperties().containsKey("code")?e.getBody().getProperties().get("code").toString():"TOOL_REJECTED";businessStatus=e.getStatusCode().value();executionStatus="NOT_DISPATCHED";var problem=new cn.superhuang.data.scalpel.web.error.ProblemDetailFactory().create(cn.superhuang.data.scalpel.web.error.ProblemType.fromStatus(e.getStatusCode()),e.getStatusCode(),Objects.toString(e.getReason(),"请求被拒绝"));problem.setProperty("code",code);if(e.getBody().getProperties()!=null&&e.getBody().getProperties().containsKey("violations"))problem.setProperty("violations",e.getBody().getProperties().get("violations"));value=Map.of("executionStatus",executionStatus,"httpStatus",businessStatus,"code",code,"problem",problem);
                }
                catch(AccessDeniedException|AuthenticationException e) {
                    failed=true;code="ACCESS_DENIED";businessStatus=403;executionStatus="NOT_DISPATCHED";value=Map.of("executionStatus","NOT_DISPATCHED","httpStatus",403,"code",code,"message","接口未开放或当前身份不可访问");
                }
                catch(RuntimeException e) {
                    failed=true;code="INTERNAL_ERROR";value=Map.of("executionStatus","UNKNOWN","code",code,"message","工具处理异常，请查询业务状态后再决定后续操作");
                }
                Map<String,Object> metadata=new LinkedHashMap<>();metadata.put("httpStatus",businessStatus);metadata.put("executionStatus",executionStatus);
                audits.record("TOOL_CALL",identity.getName(),identity.tokenId(),operation,name,failed?"ERROR":"SUCCESS",code,(System.nanoTime()-start)/1000000,mapper.writeValueAsString(metadata));
                Map<String,Object> structured=mapper.convertValue(value,Map.class);return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent(mapper.writeValueAsString(value))),failed,structured,null);
            });
        }
        server=builder.build();
    }
    public ResponseEntity<?> handle(SystemMcpAuthentication identity,byte[] bytes,String version) {
        if(version!=null&&!VERSIONS.contains(version))throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"不支持的 MCP 协议版本");
        JsonNode request;
        try {
            request=mapper.readTree(bytes);
        }
        catch(RuntimeException e) {
            return error(null,-32700,"Parse error");
        }
        JsonNode id=request==null?null:request.get("id");
        if(request==null||!request.isObject()||!"2.0".equals(request.path("jsonrpc").asText())||!request.path("method").isTextual()||id!=null&&!(id.isTextual()||id.isIntegralNumber())||request.has("params")&&!request.path("params").isObject())return error(null,-32600,"Invalid Request");
        String method=request.path("method").asText();
        if(id==null)return ResponseEntity.accepted().build();

        if(method.equals("initialize")&&(!request.path("params").path("protocolVersion").isTextual()||!request.path("params").path("capabilities").isObject()||!request.path("params").path("clientInfo").isObject()))return error(id,-32602,"Invalid params");
        if(method.equals("tools/call")&&(!request.path("params").path("name").isTextual()||request.path("params").has("arguments")&&!request.path("params").path("arguments").isObject()))return error(id,-32602,"Invalid params");
        try {
            var rpc=new McpSchema.JSONRPCRequest("2.0",method,mapper.convertValue(id,Object.class),request.has("params")?mapper.convertValue(request.get("params"),Object.class):null);
            var response=transport.handler.handleRequest(McpTransportContext.create(Map.of("identity",identity)),rpc).block(properties.getInvokeTimeout().plusSeconds(3));
            return ResponseEntity.ok().contentType(org.springframework.http.MediaType.APPLICATION_JSON).body(sdk.convertValue(response,new TypeRef<Map<String,Object>>() {
            }));
        }
        catch(RuntimeException e) {
            return error(id,-32603,"Internal error");
        }
    }
    public ResponseEntity<?> error(JsonNode id,int code,String message) {
        Map<String,Object> body=new LinkedHashMap<>();
        body.put("jsonrpc","2.0");
        body.put("id",id);
        body.put("error",Map.of("code",code,"message",message));
        return ResponseEntity.ok().body(body);
    }
    @PreDestroy public void close() {
        server.close();
    }
    private static final class Transport implements McpStatelessServerTransport {
        private McpStatelessServerHandler handler;
        public void setMcpHandler(McpStatelessServerHandler h) {
            handler=h;
        }
        public Mono<Void> closeGracefully() {
            return Mono.empty();
        }
        public List<String> protocolVersions() {
            return VERSIONS;
        }
    }
}
