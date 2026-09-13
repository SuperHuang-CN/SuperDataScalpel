package cn.superhuang.data.scalpel.business.systemmcp.security;
import cn.superhuang.data.scalpel.business.systemmcp.service.*;
import cn.superhuang.data.scalpel.web.error.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.context.SecurityContextRepository;
import java.io.IOException;
public class SystemMcpTokenFilter extends OncePerRequestFilter {
    private final SystemMcpTokenService tokens;
    private final ProblemDetailWriter errors;
    private final SecurityContextRepository contexts;
    public SystemMcpTokenFilter(SystemMcpTokenService t,ProblemDetailWriter e,SecurityContextRepository contexts) {
        tokens=t;
        errors=e;
        this.contexts=contexts;
    }
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws IOException,ServletException {
        String header=req.getHeader("Authorization");
        try {
            var identity=tokens.authenticate(header!=null&&header.startsWith("Bearer ")?header.substring(7):null);
            var context=SecurityContextHolder.createEmptyContext();
            context.setAuthentication(identity);
            SecurityContextHolder.setContext(context);
            // DeferredResult redispatches through security on another servlet thread.
            contexts.saveContext(context,req,res);
        }
        catch(AuthenticationException e) {
            errors.write(req,res,ProblemType.AUTHENTICATION_REQUIRED,"系统 MCP 令牌无效或已失效");
            return;
        }
        chain.doFilter(req,res);
    }
}
