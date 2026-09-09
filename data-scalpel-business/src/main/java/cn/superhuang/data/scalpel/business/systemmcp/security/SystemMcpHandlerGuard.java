package cn.superhuang.data.scalpel.business.systemmcp.security;
import cn.superhuang.data.scalpel.business.systemmcp.service.SystemMcpCatalogService;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.servlet.*;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.method.HandlerMethod;
@Component
public class SystemMcpHandlerGuard implements HandlerInterceptor,WebMvcConfigurer {
    private final SystemMcpAuthorization authorization;
    private final SystemMcpCatalogService catalog;
    public SystemMcpHandlerGuard(SystemMcpAuthorization a,SystemMcpCatalogService c) {
        authorization=a;
        catalog=c;
    }
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this);
    }
    @Override public boolean preHandle(HttpServletRequest req,HttpServletResponse res,Object handler) {
        if(!(SecurityContextHolder.getContext().getAuthentication() instanceof SystemMcpAuthentication identity))return true;
        String path=req.getRequestURI().substring(req.getContextPath().length());
        if(path.equals("/system-mcp")) {
            catalog.requireEnabled();
            return true;
        }
        if(!(handler instanceof HandlerMethod method))throw new AccessDeniedException("系统令牌不能访问此资源");
        Object pattern=req.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String id=req.getMethod()+" "+pattern;
        authorization.require(id,identity);
        var expected=catalog.handler(id);
        if(expected==null||!expected.getMethod().equals(method.getMethod()))throw new AccessDeniedException("接口路由已变化，请同步目录");
        return true;
    }
}
