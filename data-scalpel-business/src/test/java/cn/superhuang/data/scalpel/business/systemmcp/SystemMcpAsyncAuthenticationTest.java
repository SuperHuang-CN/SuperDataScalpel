package cn.superhuang.data.scalpel.business.systemmcp;

import cn.superhuang.data.scalpel.business.systemmcp.config.SystemMcpProperties;
import cn.superhuang.data.scalpel.business.systemmcp.security.SystemMcpAuthentication;
import cn.superhuang.data.scalpel.business.systemmcp.security.SystemMcpTokenFilter;
import cn.superhuang.data.scalpel.business.systemmcp.service.SystemMcpProtocolService;
import cn.superhuang.data.scalpel.business.systemmcp.service.SystemMcpTokenService;
import cn.superhuang.data.scalpel.business.systemmcp.web.resource.SystemMcpProtocolResource;
import cn.superhuang.data.scalpel.web.error.ProblemDetailFactory;
import cn.superhuang.data.scalpel.web.error.ProblemDetailWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authorization.AuthenticatedAuthorizationManager;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestFilter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SystemMcpAsyncAuthenticationTest {
    private final SystemMcpTokenService tokens = mock(SystemMcpTokenService.class);
    private final SystemMcpProtocolService protocol = mock(SystemMcpProtocolService.class);
    private final SystemMcpProtocolResource resource = new SystemMcpProtocolResource(protocol, new SystemMcpProperties());
    private final SystemMcpAuthentication identity = new SystemMcpAuthentication("reader", UUID.randomUUID(), "dssmcp_test", List.of());

    private MockMvc mvc() throws Exception {
        var contexts = new RequestAttributeSecurityContextRepository();
        var mapper = JsonMapper.builder().addMixIn(org.springframework.http.ProblemDetail.class,
                org.springframework.http.converter.json.ProblemDetailJacksonMixin.class).build();
        var writer = new ProblemDetailWriter(mapper, new ProblemDetailFactory());
        var servletIdentity = new SecurityContextHolderAwareRequestFilter();
        servletIdentity.afterPropertiesSet();
        var filters = new FilterChainProxy(new DefaultSecurityFilterChain(request -> true,
                new SecurityContextHolderFilter(contexts),
                new SystemMcpTokenFilter(tokens, writer, contexts),
                servletIdentity,
                new AuthorizationFilter(AuthenticatedAuthorizationManager.authenticated())));
        return MockMvcBuilders.standaloneSetup(resource).addFilters(filters).build();
    }

    @AfterEach
    void close() {
        resource.close();
        SecurityContextHolder.clearContext();
    }

    @Test
    void deferredResponseRetainsAuthenticationWithoutCreatingSession() throws Exception {
        when(tokens.authenticate("dssmcp_test")).thenReturn(identity);
        doReturn(ResponseEntity.ok(Map.of("jsonrpc", "2.0", "id", 1, "result", Map.of())))
                .when(protocol).handle(same(identity), any(byte[].class), isNull());
        var mvc = mvc();
        var initial = mvc.perform(post("/system-mcp")
                        .header("Authorization", "Bearer dssmcp_test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}"))
                .andExpect(request().asyncStarted()).andReturn();
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        initial.getAsyncResult(5000);
        mvc.perform(asyncDispatch(initial)).andExpect(status().isOk())
                .andExpect(jsonPath("$.result").isMap());
        assertNull(initial.getRequest().getSession(false));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(tokens, times(1)).authenticate("dssmcp_test");

        // A separate HTTP request must authenticate again, including after revocation.
        when(tokens.authenticate("dssmcp_test")).thenThrow(new BadCredentialsException("revoked"));
        mvc.perform(post("/system-mcp").header("Authorization", "Bearer dssmcp_test")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized()).andExpect(request().asyncNotStarted())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        verify(tokens, times(2)).authenticate("dssmcp_test");
        verify(protocol, times(1)).handle(same(identity), any(byte[].class), isNull());
    }

    @Test
    void missingTokenNeverEntersProtocol() throws Exception {
        when(tokens.authenticate(null)).thenThrow(new BadCredentialsException("missing"));
        mvc().perform(post("/system-mcp").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized()).andExpect(request().asyncNotStarted())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        verifyNoInteractions(protocol);
    }

    @Test
    void workerErrorCompletesDeferredResponseInsteadOfWaitingForTimeout() throws Exception {
        var errors = new java.util.concurrent.LinkedBlockingQueue<Throwable>();
        var previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> errors.add(error));
        try {
            doThrow(new StackOverflowError("schema cycle")).when(protocol)
                    .handle(same(identity), any(byte[].class), isNull());
            var request = new org.springframework.mock.web.MockHttpServletRequest();
            request.setContent("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var deferred = resource.handle(request, identity);
            assertInstanceOf(StackOverflowError.class, errors.poll(5, java.util.concurrent.TimeUnit.SECONDS));
            assertTrue(deferred.hasResult());
            assertInstanceOf(IllegalStateException.class, deferred.getResult());
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous);
        }
    }
}
