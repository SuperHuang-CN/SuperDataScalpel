package cn.superhuang.data.scalpel.business.systemmcp.security;
import cn.superhuang.data.scalpel.business.systemmcp.service.SystemMcpCatalogService;
import cn.superhuang.data.scalpel.business.systemmcp.repository.SystemMcpApiRepository;
import cn.superhuang.data.scalpel.business.systemmcp.domain.SystemMcpApi;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationContext;
import org.springframework.security.authorization.method.PreAuthorizeAuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;
import org.aopalliance.intercept.MethodInvocation;
import java.lang.reflect.*;
@Service
public class SystemMcpAuthorization {
    private final SystemMcpCatalogService catalog;
    private final SystemMcpApiRepository apis;
    private final PreAuthorizeAuthorizationManager manager=new PreAuthorizeAuthorizationManager();
    public SystemMcpAuthorization(SystemMcpCatalogService c,SystemMcpApiRepository a,ApplicationContext context) {
        catalog=c;
        apis=a;
        manager.setApplicationContext(context);
    }
    public boolean permitted(SystemMcpApi api,Authentication identity) {
        var h=catalog.handler(api.getOperationId());
        if(h==null||!api.getEnabled()||!"AVAILABLE".equals(api.getStatus()))return false;
        MethodInvocation invocation=new MethodInvocation() {
            public Method getMethod() {
                return h.getMethod();
            }
            public Object[] getArguments() {
                return new Object[h.getMethod().getParameterCount()];
            }
            public Object proceed() {
                throw new UnsupportedOperationException();
            }
            public Object getThis() {
                return h.getBean();
            }
            public AccessibleObject getStaticPart() {
                return h.getMethod();
            }
        };
        try {
            var result=manager.authorize(()->identity,invocation);
            return result!=null&&result.isGranted();
        }
        catch(RuntimeException e) {
            return false;
        }
    }
    public SystemMcpApi require(String id,Authentication identity) {
        catalog.requireEnabled();
        var api=apis.findByOperationId(id).orElseThrow(()->new AccessDeniedException("接口不可访问"));
        if(!permitted(api,identity))throw new AccessDeniedException("接口未开放或用户无权访问");
        return api;
    }
}
