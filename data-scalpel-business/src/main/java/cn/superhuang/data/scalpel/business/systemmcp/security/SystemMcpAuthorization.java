package cn.superhuang.data.scalpel.business.systemmcp.security;
import cn.superhuang.data.scalpel.business.systemmcp.service.SystemMcpCatalogService;
import cn.superhuang.data.scalpel.business.systemmcp.repository.SystemMcpApiRepository;
import cn.superhuang.data.scalpel.business.systemmcp.domain.SystemMcpApi;
import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;
@Service
public class SystemMcpAuthorization {
    private final SystemMcpCatalogService catalog;
    private final SystemMcpApiRepository apis;
    public SystemMcpAuthorization(SystemMcpCatalogService c,SystemMcpApiRepository a) {
        catalog=c;
        apis=a;
    }
    public boolean permitted(SystemMcpApi api,Authentication identity) {
        return identity instanceof SystemMcpAuthentication && identity.isAuthenticated()
                && api.getEnabled() && "AVAILABLE".equals(api.getStatus())
                && catalog.handler(api.getOperationId())!=null;
    }
    public SystemMcpApi require(String id,Authentication identity) {
        catalog.requireEnabled();
        var api=apis.findByOperationId(id).orElseThrow(()->new AccessDeniedException("接口不可访问"));
        if(!permitted(api,identity))throw new AccessDeniedException("接口未开放或当前不可用");
        return api;
    }
}
