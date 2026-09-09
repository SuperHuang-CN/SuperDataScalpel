package cn.superhuang.data.scalpel.business.systemmcp.service;
import cn.superhuang.data.scalpel.business.systemmcp.security.*;
import cn.superhuang.data.scalpel.business.systemmcp.repository.*;
import cn.superhuang.data.scalpel.business.systemmcp.domain.*;
import cn.superhuang.data.scalpel.business.systemmcp.config.*;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.*;
import java.util.*;
@Service
public class SystemMcpToolService {
    private final SystemMcpTokenService tokens;
    private final SystemMcpAuthorization auth;
    private final SystemMcpApiRepository apis;
    private final SystemMcpCatalogService catalog;
    private final SystemMcpManagementService management;
    private final SystemMcpInvocationService invoke;
    private final ObjectMapper mapper;
    private final SystemMcpProperties properties;
    public SystemMcpToolService(SystemMcpTokenService t,SystemMcpAuthorization a,SystemMcpApiRepository r,SystemMcpCatalogService c,SystemMcpManagementService m,SystemMcpInvocationService i,ObjectMapper j,SystemMcpProperties p) {
        tokens=t;
        auth=a;
        apis=r;
        catalog=c;
        management=m;
        invoke=i;
        mapper=j;
        properties=p;
    }
    public Object call(String tool,SystemMcpAuthentication original,JsonNode args) {
        var identity=tokens.authenticate(original.forwardingBearer());
        catalog.requireEnabled();
        return switch(tool) {
            case "api_search"->search(identity,args);
            case "api_describe"->describe(identity,args);
            case "api_invoke"->invoke.invoke(identity,args);
            default->throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"未知工具");
        };
    }
    private Object search(SystemMcpAuthentication identity,JsonNode args) {
        int offset=args.path("offset").asInt(0),limit=args.path("limit").asInt(10);
        if(offset<0||limit<1||limit>50)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"分页参数超出范围");
        String q=args.path("query").asText("").trim().toLowerCase(Locale.ROOT);
        String module=args.path("module").asText("");
        String effect=args.path("effect").asText("");
        var result=apis.findAllByEnabledTrueAndStatus("AVAILABLE").stream().filter(a->module.isBlank()||a.getModule().equals(module)).filter(a->effect.isBlank()||a.getEffect().equals(effect)).filter(a->score(a,q)>0).filter(a->auth.permitted(a,identity)).sorted(Comparator.<SystemMcpApi>comparingInt(a->score(a,q)).reversed().thenComparing(SystemMcpApi::getOperationId)).toList();
        return Map.of("items",result.stream().skip(offset).limit(limit).map(a->management.response(a,false)).toList(),"total",result.size(),"offset",offset,"limit",limit);
    }
    private int score(SystemMcpApi a,String q) {
        if(q.isBlank())return 1;
        String title=(a.getSummary()+" "+a.getModule()+" "+a.getKeywords()).toLowerCase(Locale.ROOT);
        String text=(title+" "+a.getDescription()+" "+a.getPath()).toLowerCase(Locale.ROOT);
        int score=0;
        for(String term:q.split("\\s+")) {
            if(!text.contains(term))return 0;
            score+=title.contains(term)?3:1;
        }
        return score;
    }
    private Object describe(SystemMcpAuthentication identity,JsonNode args) {
        JsonNode ids=args.path("operationIds");
        if(!ids.isArray()||ids.isEmpty()||ids.size()>5)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"一次需要提供 1 至 5 个接口标识");
        List<Object> result=new ArrayList<>();
        for(JsonNode id:ids) {
            var api=auth.require(id.asText(),identity);
            result.add(management.response(api,true));
        }
        if(mapper.writeValueAsBytes(result).length>properties.getMaxResponseBytes())throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"接口契约超过返回预算，请减少批量数量");
        return Map.of("items",result);
    }
}
