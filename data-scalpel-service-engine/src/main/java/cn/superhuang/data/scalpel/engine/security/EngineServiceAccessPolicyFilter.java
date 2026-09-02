package cn.superhuang.data.scalpel.engine.security;

import cn.superhuang.data.scalpel.engine.accesspolicy.EngineAccessPolicyService;
import cn.superhuang.data.scalpel.web.error.ProblemDetailWriter;
import cn.superhuang.data.scalpel.web.error.ProblemType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Enforces the Engine-level source-address policy only for deployed business services. */
@Component
public class EngineServiceAccessPolicyFilter extends OncePerRequestFilter {

    private static final String SERVICE_PREFIX = "/open-api/v1/";

    private final EngineAccessPolicyService policyService;
    private final ProblemDetailWriter problemDetailWriter;

    public EngineServiceAccessPolicyFilter(
            EngineAccessPolicyService policyService,
            ProblemDetailWriter problemDetailWriter
    ) {
        this.policyService = policyService;
        this.problemDetailWriter = problemDetailWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(SERVICE_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        EngineAccessPolicyService.PolicyDecision decision = policyService.evaluate(request.getRemoteAddr());
        if (!decision.allowed()) {
            problemDetailWriter.write(request, response, ProblemType.ACCESS_DENIED, decision.denialDetail());
            return;
        }
        filterChain.doFilter(request, response);
    }
}
