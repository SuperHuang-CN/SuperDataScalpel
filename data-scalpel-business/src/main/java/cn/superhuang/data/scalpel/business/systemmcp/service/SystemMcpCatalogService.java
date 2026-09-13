package cn.superhuang.data.scalpel.business.systemmcp.service;
import cn.superhuang.data.scalpel.business.systemmcp.domain.*;
import cn.superhuang.data.scalpel.business.systemmcp.repository.*;
import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;
import cn.superhuang.data.scalpel.business.systemmcp.web.request.UpdateSystemMcpConfigurationRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
@Service
public class SystemMcpCatalogService {
    private final RequestMappingHandlerMapping mappings;
    private final SystemMcpApiRepository apis;
    private final SystemMcpSettingRepository settings;
    private final SystemMcpLocalEndpoint endpoint;
    private final SystemMcpContractBuilder contracts;
    private final ObjectMapper mapper;
    private final cn.superhuang.data.scalpel.business.systemmcp.config.SystemMcpProperties properties;
    private final TransactionTemplate transaction;
    private volatile Map<String, HandlerMethod> handlers = Map.of();
    private volatile String status = "NOT_READY";
    private volatile String message = "目录尚未同步";
    private volatile Instant updatedAt;
    public SystemMcpCatalogService(@org.springframework.context.annotation.Lazy @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings,
    SystemMcpApiRepository apis, SystemMcpSettingRepository settings, SystemMcpLocalEndpoint endpoint,
    SystemMcpContractBuilder contracts, ObjectMapper mapper, PlatformTransactionManager manager, cn.superhuang.data.scalpel.business.systemmcp.config.SystemMcpProperties properties) {
        this.properties = properties;
        this.mappings=mappings;
        this.apis=apis;
        this.settings=settings;
        this.endpoint=endpoint;
        this.contracts=contracts;
        this.mapper=mapper;
        transaction=new TransactionTemplate(manager);
    }
    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        try {
            refresh();
        } catch (RuntimeException ignored) {
            /* Status is visible in configuration. */
        }
    }
    public synchronized void refresh() {
        status="SYNCING";
        message="正在同步当前部署接口";
        try {
            JsonNode document;
            try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build()) {
                var response=client.send(HttpRequest.newBuilder(endpoint.base().resolve(endpoint.base().getPath()+"/v3/api-docs"))
                .timeout(Duration.ofSeconds(60)).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
                try(var stream=response.body()) {
                    byte[] bytes=stream.readNBytes(16*1024*1024+1);
                    if(response.statusCode()!=200 || bytes.length>16*1024*1024) throw new IllegalStateException("OpenAPI 不可用或超过目录预算");
                    document=mapper.readTree(bytes);
                }
            }
            if(!document.path("paths").isObject() || document.path("paths").isEmpty()) throw new IllegalStateException("OpenAPI 路由目录为空");
            Map<String, List<Candidate>> candidates=new TreeMap<>();
            mappings.getHandlerMethods().forEach((mapping, handler) -> {
                for(String path:mapping.getPatternValues()) {
                    if(!eligiblePath(path)) continue;
                    for(RequestMethod method:mapping.getMethodsCondition().getMethods()) {
                        if(method!=RequestMethod.GET && method!=RequestMethod.POST) continue;
                        String key=method.name()+" "+path;
                        boolean conditional=!mapping.getParamsCondition().isEmpty() || !mapping.getHeadersCondition().isEmpty();
                        candidates.computeIfAbsent(key, ignored->new ArrayList<>()).add(new Candidate(method.name(),path,handler.createWithResolvedBean(),conditional));
                    }
                }
            });
            if(candidates.isEmpty()) throw new IllegalStateException("没有发现业务接口");
            Map<String, HandlerMethod> nextHandlers=new HashMap<>();
            List<SystemMcpApi> collected=new ArrayList<>();
            for(var entry:candidates.entrySet()) {
                Candidate c=entry.getValue().getFirst();
                SystemMcpApi api=describe(c,document);
                if(entry.getValue().size()!=1 || c.conditional()) unsupported(api,"相同路由依赖额外匹配条件，需要适配");
                if(api.getStatus().equals("AVAILABLE")) nextHandlers.put(entry.getKey(),c.handler());
                collected.add(api);
            }
            transaction.executeWithoutResult(tx -> {
                Map<String,SystemMcpApi> existing=new HashMap<>();
                apis.findAll().forEach(a->existing.put(a.getOperationId(),a));
                for(SystemMcpApi source:collected) {
                    SystemMcpApi target=existing.remove(source.getOperationId());
                    if(target==null) target=new SystemMcpApi();
                    copy(source,target);
                    if(!target.getStatus().equals("AVAILABLE")) target.setEnabled(false);
                    apis.save(target);
                }
                for(SystemMcpApi removed:existing.values()) {
                    removed.setStatus("REMOVED"); removed.setEnabled(false); removed.setUnavailableReason("当前部署已移除此接口");
                }
                settings.findBySettingKey("global").orElseGet(()-> {
                    var s=new SystemMcpSetting();s.setSettingKey("global");return settings.save(s);
                });
            });
            handlers=Map.copyOf(nextHandlers);
            updatedAt=Instant.now();
            message=null;
            status="READY";
        } catch(Exception exception) {
            if(exception instanceof InterruptedException) Thread.currentThread().interrupt();
            status="ERROR";
            message="接口目录同步失败，请检查当前部署的 OpenAPI 文档与接口声明";
            org.slf4j.LoggerFactory.getLogger(getClass()).error("System MCP catalog refresh failed",exception);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,message);
        }
    }
    private SystemMcpApi describe(Candidate c,JsonNode document) {
        SystemMcpApi a=new SystemMcpApi();
        a.setOperationId(c.method()+" "+c.path());
        a.setMethod(c.method());
        a.setPath(c.path());
        var handler=c.handler();
        var tag=AnnotatedElementUtils.findMergedAnnotation(handler.getBeanType(),Tag.class);
        var op=AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(),Operation.class);
        var semantics=AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(),SystemMcpOperation.class);
        a.setModule(tag==null?handler.getBeanType().getSimpleName():tag.name());
        String summary=semantics!=null&&!semantics.summary().isBlank()?semantics.summary():op==null?"":op.summary();
        a.setSummary(summary.isBlank()?handler.getMethod().getName():summary);
        a.setDescription(op==null?"":op.description());
        a.setEffect(semantics==null?"UNKNOWN":semantics.value().name());
        a.setKeywords(semantics==null?"":String.join(" ",semantics.keywords()));
        a.setStatus("AVAILABLE");
        try {
            if(semantics==null || summary.isBlank()) {
                a.setStatus("INCOMPLETE");
                a.setUnavailableReason("接口用途或操作性质尚未声明");
                return a;
            }
            var auth=AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(),PreAuthorize.class);
            if(auth==null) auth=AnnotatedElementUtils.findMergedAnnotation(handler.getBeanType(),PreAuthorize.class);
            if(auth==null || auth.value().contains("#")) throw new IllegalArgumentException("接口授权需要显式适配");
            String type=handler.getMethod().getGenericReturnType().getTypeName();
            if(type.contains("byte[]")||type.contains("org.springframework.core.io.")||type.contains("StreamingResponseBody")||type.contains("Emitter")
            ||type.contains("InputStream")||type.contains("reactor.")||type.equals("java.lang.String"))
            throw new IllegalArgumentException("第一版不支持文件、文本或流式响应");
            for(var parameter:handler.getMethod().getGenericParameterTypes())
            if(parameter.getTypeName().contains("Multipart")||parameter.getTypeName().contains("HttpServletResponse"))
            throw new IllegalArgumentException("第一版不支持文件或直接响应接口");
            JsonNode pathItem=document.path("paths").path(c.path());
            JsonNode operation=pathItem.path(c.method().toLowerCase(Locale.ROOT));
            if(operation.isMissingNode()) throw new IllegalArgumentException("OpenAPI 尚未描述此接口");
            for(JsonNode p:operation.path("parameters"))
            if(p.path("in").asText().equals("header")||p.path("in").asText().equals("cookie")) throw new IllegalArgumentException("自定义请求头或 Cookie 接口需要适配");
            for(String code:operation.path("responses").propertyNames()) if(code.startsWith("2")) {
                JsonNode content=operation.path("responses").path(code).path("content");
                for(String media:content.propertyNames()) if(!media.equals("*/*")&&!media.equals("application/json")&&!(media.startsWith("application/")&&media.endsWith("+json")))
                throw new IllegalArgumentException("第一版仅支持 JSON 和空响应");
            }
            ObjectNode contract=contracts.build(document,pathItem,operation);
            contract.put("operationId",a.getOperationId());
            contract.put("summary",a.getSummary());
            contract.put("description",a.getDescription());
            contract.put("effect",a.getEffect());
            contract.put("prerequisites",semantics.prerequisites());
            contract.set("relatedOperations",mapper.valueToTree(semantics.relatedOperations()));
            if (mapper.writeValueAsBytes(contract).length > properties.getMaxResponseBytes()) throw new IllegalArgumentException("接口 Schema 超过详情预算，需要适配");
            a.setContractJson(contract.toString());
            a.setFingerprint(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(a.getContractJson().getBytes(StandardCharsets.UTF_8))));
        }
        catch(Exception e) {
            unsupported(a,e instanceof IllegalArgumentException?e.getMessage():"接口 Schema 需要适配");
        }
        return a;
    }
    private static void unsupported(SystemMcpApi a,String reason) {
        a.setStatus("UNSUPPORTED");
        a.setUnavailableReason(reason);
        a.setEnabled(false);
    }
    private static void copy(SystemMcpApi s,SystemMcpApi t) {
        t.setOperationId(s.getOperationId());
        t.setMethod(s.getMethod());
        t.setPath(s.getPath());
        t.setModule(s.getModule());
        t.setSummary(s.getSummary());
        t.setDescription(s.getDescription());
        t.setKeywords(s.getKeywords());
        t.setEffect(s.getEffect());
        t.setStatus(s.getStatus());
        t.setUnavailableReason(s.getUnavailableReason());
        t.setContractJson(s.getContractJson());
        t.setFingerprint(s.getFingerprint());
    }
    public synchronized List<Map<String,Object>> updateConfiguration(UpdateSystemMcpConfigurationRequest request) {
        if (Boolean.TRUE.equals(request.enabled()) || request.changes() != null && request.changes().stream().anyMatch(c -> Boolean.TRUE.equals(c.enabled()))) requireReady();
        return transaction.execute(tx-> {
            List<Map<String,Object>> changes=new ArrayList<>();
            var global=settings.findBySettingKey("global").orElseGet(()-> {
                var setting=new SystemMcpSetting();setting.setSettingKey("global");return settings.save(setting);
            });
            if(request.enabled()!=null&&request.enabled()!=global.getEnabled()) {
                changes.add(Map.of("setting","enabled","before",global.getEnabled(),"after",request.enabled()));global.setEnabled(request.enabled());
            }
            Set<UUID> seen=new HashSet<>();
            if(request.changes()!=null) for(var change:request.changes()) {
                if(!seen.add(change.id())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"接口变更重复");
                var a=apis.findById(change.id()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"接口不存在"));
                if(change.enabled()&&(!a.getStatus().equals("AVAILABLE")||!handlers.containsKey(a.getOperationId())))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"接口当前不可开放："+a.getSummary());
                if(a.getEnabled()!=change.enabled()) {
                    changes.add(Map.of("operationId",a.getOperationId(),"before",a.getEnabled(),"after",change.enabled()));a.setEnabled(change.enabled());
                }
            }
            return changes;
        });
    }
    public static boolean eligiblePath(String path) {
        return path.startsWith("/api/v1/")&&!under(path,"/api/v1/auth")&&!under(path,"/api/v1/internal")&&!under(path,"/api/v1/system-mcp")&&!under(path,"/api/v1/dsh");
    }
    private static boolean under(String value,String root) {
        return value.equals(root)||value.startsWith(root+"/");
    }
    public void requireReady() {
        if(!status.equals("READY"))throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"系统 MCP 接口目录未就绪");
    }
    public void requireEnabled() {
        requireReady();
        if(!settings.findBySettingKey("global").map(SystemMcpSetting::getEnabled).orElse(false))throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"系统 MCP 已关闭");
    }
    public HandlerMethod handler(String id) {
        return handlers.get(id);
    }
    public String status() {
        return status;
    } public String message() {
        return message;
    } public Instant updatedAt() {
        return updatedAt;
    }
    private record Candidate(String method,String path,HandlerMethod handler,boolean conditional) {
    }
}
