package cn.superhuang.data.scalpel.business.mcp.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class McpTokenAuthenticationFilter extends OncePerRequestFilter {
    public static final String AUTHENTICATED_SERVER_ATTRIBUTE=McpTokenAuthenticationFilter.class.getName()+".server";
    private final McpInvocationAuthenticationService service;
    public McpTokenAuthenticationFilter(McpInvocationAuthenticationService service){this.service=service;}
    @Override protected boolean shouldNotFilter(HttpServletRequest request){return !request.getRequestURI().startsWith("/mcp/");}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
        String header=request.getHeader("Authorization"); String token=header!=null&&header.startsWith("Bearer ")?header.substring(7).trim():null;
        String code=request.getRequestURI().substring("/mcp/".length()); int slash=code.indexOf('/'); if(slash>=0)code=code.substring(0,slash);
        var result=service.authenticate(code,token);
        if(result.validToken()){
            var authorities=result.authorized()?List.of(new SimpleGrantedAuthority("mcp.invoke")):List.of(new SimpleGrantedAuthority("mcp.authenticated"));
            var authentication=new UsernamePasswordAuthenticationToken("mcp-token:"+result.tokenId(),null,authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            if(result.authorized())request.setAttribute(AUTHENTICATED_SERVER_ATTRIBUTE,result.server());
        }
        chain.doFilter(request,response);
    }
}
