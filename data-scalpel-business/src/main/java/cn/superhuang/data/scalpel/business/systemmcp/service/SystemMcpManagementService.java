package cn.superhuang.data.scalpel.business.systemmcp.service;
import cn.superhuang.data.scalpel.business.systemmcp.domain.*;
import cn.superhuang.data.scalpel.business.systemmcp.repository.*;
import cn.superhuang.data.scalpel.business.systemmcp.config.*;
import cn.superhuang.data.scalpel.business.systemmcp.web.response.*;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import cn.superhuang.data.scalpel.search.SearchEngine;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
@Service
public class SystemMcpManagementService {
    private final SystemMcpApiRepository apis;
    private final SystemMcpSettingRepository settings;
    private final SystemMcpAccessTokenRepository tokens;
    private final SystemMcpAuditRepository audits;
    private final SystemMcpCatalogService catalog;
    private final SystemMcpTokenService tokenService;
    private final SystemMcpProperties properties;
    private final SearchEngine search;
    private final ObjectMapper mapper;
    private final SystemMcpAuditService audit;
    public SystemMcpManagementService(SystemMcpApiRepository a,SystemMcpSettingRepository s,SystemMcpAccessTokenRepository t,SystemMcpAuditRepository l,SystemMcpCatalogService c,SystemMcpTokenService ts,SystemMcpProperties p,SearchEngine e,ObjectMapper m,SystemMcpAuditService audit) {
        this.audit=audit;
        apis=a;
        settings=s;
        tokens=t;
        audits=l;
        catalog=c;
        tokenService=ts;
        properties=p;
        search=e;
        mapper=m;
    }
    public SystemMcpConfigurationResponse configuration() {
        var all=apis.findAll();
        String base=properties.getPublicBaseUrl().replaceAll("/$","");
        return new SystemMcpConfigurationResponse(settings.findBySettingKey("global").map(SystemMcpSetting::getEnabled).orElse(false),base+"/system-mcp",catalog.status(),catalog.message(),catalog.updatedAt(),all.size(),all.stream().filter(a->a.getStatus().equals("AVAILABLE")).count(),all.stream().filter(SystemMcpApi::getEnabled).count());
    }
    public SystemMcpApiResponse response(SystemMcpApi a,boolean detail) {
        return new SystemMcpApiResponse(a.getId(),a.getOperationId(),a.getMethod(),a.getPath(),a.getModule(),a.getSummary(),a.getDescription(),a.getEffect(),a.getStatus(),a.getUnavailableReason(),a.getEnabled(),a.getFingerprint(),a.getUpdatedAt(),detail&&a.getContractJson()!=null?mapper.readTree(a.getContractJson()):null);
    }
    public SystemMcpApiResponse api(UUID id) {
        return response(apis.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"接口不存在")),true);
    }
    public PageResponse<SystemMcpApiResponse> apis(SearchRequest r) {
        var p=search.search(r,SystemMcpApi.class,apis);
        return new PageResponse<>(p.getContent().stream().map(a->response(a,false)).toList(),p.getTotalElements(),p.getTotalPages(),p.getNumber(),p.getSize());
    }
    public List<SystemMcpModuleResponse> modules() {
        return apis.findAll().stream()
                .collect(java.util.stream.Collectors.groupingBy(SystemMcpApi::getModule, TreeMap::new, java.util.stream.Collectors.toList()))
                .entrySet().stream()
                .map(entry -> new SystemMcpModuleResponse(
                        entry.getKey(),
                        entry.getValue().size(),
                        entry.getValue().stream().filter(api -> api.getStatus().equals("AVAILABLE")).count(),
                        entry.getValue().stream().filter(SystemMcpApi::getEnabled).count()))
                .toList();
    }
    public PageResponse<SystemMcpTokenResponse> tokens(SearchRequest r) {
        var p=search.search(r,SystemMcpAccessToken.class,tokens);
        return new PageResponse<>(tokenService.responses(p.getContent()),p.getTotalElements(),p.getTotalPages(),p.getNumber(),p.getSize());
    }
    public PageResponse<SystemMcpAuditResponse> audits(SearchRequest r) {
        var p=search.search(r,SystemMcpAudit.class,audits);
        return new PageResponse<>(p.getContent().stream().map(a->new SystemMcpAuditResponse(a.getId(),a.getEventType(),a.getUsername(),a.getTokenId(),a.getOperationId(),a.getToolName(),a.getStatus(),a.getErrorCode(),a.getDurationMs(),a.getChangesJson(),a.getCreatedAt())).toList(),p.getTotalElements(),p.getTotalPages(),p.getNumber(),p.getSize());
    }
    public SystemMcpConfigurationResponse updateConfiguration(cn.superhuang.data.scalpel.business.systemmcp.web.request.UpdateSystemMcpConfigurationRequest request,String username) {
        var changes=catalog.updateConfiguration(request);
        audit.record("CONFIGURATION",username,null,null,null,"SUCCESS",null,0,mapper.writeValueAsString(changes));
        return configuration();
    }
    public SystemMcpConfigurationResponse refreshCatalog(String username) {
        try {
            catalog.refresh();
            audit.record("CATALOG_REFRESH",username,null,null,null,"SUCCESS",null,0,null);
        }
        catch(RuntimeException e) {
            audit.record("CATALOG_REFRESH",username,null,null,null,"ERROR","CATALOG_SYNC_FAILED",0,null);
            throw e;
        }
        return configuration();
    }
    public SystemMcpIssuedTokenResponse createToken(cn.superhuang.data.scalpel.business.systemmcp.web.request.CreateSystemMcpTokenRequest r,String username) {
        var issued=tokenService.create(r);
        tokenEvent(username,issued.token(),"CREATE");
        return issued;
    }
    public SystemMcpIssuedTokenResponse rotateToken(UUID id,String username) {
        var issued=tokenService.rotate(id);
        tokenEvent(username,issued.token(),"ROTATE");
        return issued;
    }
    public SystemMcpTokenResponse updateToken(UUID id,cn.superhuang.data.scalpel.business.systemmcp.web.request.UpdateSystemMcpTokenRequest r,String username) {
        var response=tokenService.update(id,r);
        tokenEvent(username,response,"UPDATE");
        return response;
    }
    public void enableToken(UUID id,boolean value,String username) {
        tokenService.enabled(id,value);
        tokenEvent(username,tokenService.response(tokenService.get(id)),value?"ENABLE":"DISABLE");
    }
    public void deleteToken(UUID id,String username) {
        var previous=tokenService.response(tokenService.get(id));
        tokenService.delete(id);
        tokenEvent(username,previous,"DELETE");
    }
    private void tokenEvent(String username,SystemMcpTokenResponse t,String action) {
        Map<String,Object> changes=new LinkedHashMap<>();
        changes.put("name",t.name());
        changes.put("userId",t.userId());
        changes.put("enabled",t.enabled());
        changes.put("revision",t.revision());
        changes.put("expiresAt",t.expiresAt());
        audit.record("TOKEN_"+action,username,t.id(),null,null,"SUCCESS",null,0,mapper.writeValueAsString(changes));
    }
}
