package cn.superhuang.data.scalpel.business.systemmcp.security;
import cn.superhuang.data.scalpel.business.systemmcp.service.*;
import cn.superhuang.data.scalpel.web.error.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.AuthenticationException;
import java.io.IOException;
public class SystemMcpTokenFilter extends OncePerRequestFilter {
    private final SystemMcpTokenService tokens;
    private final ProblemDetailWriter errors;
    public SystemMcpTokenFilter(SystemMcpTokenService t,ProblemDetailWriter e) {
        tokens=t;
        errors=e;
    }
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws IOException,ServletException {
        String header=req.getHeader("Authorization");
        try {
            var identity=tokens.authenticate(header!=null&&header.startsWith("Bearer ")?header.substring(7):null);
            var context=SecurityContextHolder.createEmptyContext();
            context.setAuthentication(identity);
            SecurityContextHolder.setContext(context);
        }
        catch(AuthenticationException e) {
            errors.write(req,res,ProblemType.AUTHENTICATION_REQUIRED,"系统 MCP 令牌无效或已失效");
            return;
        }
        chain.doFilter(req,res);
    }
}
