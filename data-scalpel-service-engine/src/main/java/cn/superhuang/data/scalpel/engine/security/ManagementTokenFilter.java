package cn.superhuang.data.scalpel.engine.security;

import cn.superhuang.data.scalpel.engine.config.EngineProperties;
import cn.superhuang.data.scalpel.web.error.ProblemDetailWriter;
import cn.superhuang.data.scalpel.web.error.ProblemType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Simple fixed-token authentication for the Engine control-plane endpoints. */
@Component
public class ManagementTokenFilter extends OncePerRequestFilter {

    private static final String INTERNAL_PREFIX = "/internal/v1/";
    private final byte[] expectedToken;
    private final ProblemDetailWriter problemDetailWriter;

    public ManagementTokenFilter(EngineProperties properties, ProblemDetailWriter problemDetailWriter) {
        this.expectedToken = properties.managementToken().getBytes(StandardCharsets.UTF_8);
        this.problemDetailWriter = problemDetailWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(INTERNAL_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            problemDetailWriter.write(request, response, ProblemType.AUTHENTICATION_REQUIRED, "缺少服务引擎管理令牌");
            return;
        }
        byte[] suppliedToken = authorization.substring("Bearer ".length()).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedToken, suppliedToken)) {
            problemDetailWriter.write(request, response, ProblemType.AUTHENTICATION_REQUIRED, "服务引擎管理令牌无效");
            return;
        }
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "engine-management", null, AuthorityUtils.createAuthorityList("engine.manage")
        ));
        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
