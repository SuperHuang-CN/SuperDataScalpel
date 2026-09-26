package cn.superhuang.data.scalpel.admin.config;

import cn.superhuang.data.scalpel.business.task.service.JavaLanguageRelay;
import cn.superhuang.data.scalpel.business.task.service.JavaLanguageTicketService;
import cn.superhuang.data.scalpel.web.error.ProblemDetailFactory;
import cn.superhuang.data.scalpel.web.error.ProblemType;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;
import tools.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
@EnableWebSocket
public class JavaLanguageWebSocketConfiguration implements WebSocketConfigurer {
    private final JavaLanguageRelay relay;
    private final JavaLanguageTicketService tickets;
    private final ProblemDetailFactory problems;
    private final ObjectMapper json;
    public JavaLanguageWebSocketConfiguration(JavaLanguageRelay relay, JavaLanguageTicketService tickets,
                                             ProblemDetailFactory problems, ObjectMapper json) {
        this.relay = relay; this.tickets = tickets; this.problems = problems; this.json = json;
    }
    @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(relay, JavaLanguageTicketService.PATH).addInterceptors(new HandshakeInterceptor() {
            @Override public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                                      WebSocketHandler handler, Map<String, Object> attributes) throws Exception {
                String protocols = request.getHeaders().getFirst("Sec-WebSocket-Protocol");
                String ticket = protocols == null ? "" : Arrays.stream(protocols.split(","))
                        .map(String::trim).filter(value -> value.startsWith("ticket.")).findFirst().orElse("");
                try { attributes.put(JavaLanguageRelay.TICKET_ATTRIBUTE, tickets.consume(ticket)); return true; }
                catch (ResponseStatusException ex) {
                    response.setStatusCode(HttpStatus.UNAUTHORIZED);
                    response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
                    response.getBody().write(json.writeValueAsBytes(problems.create(ProblemType.AUTHENTICATION_REQUIRED,
                            "代码提示连接凭证已失效，请重新连接")));
                    return false;
                }
            }
            @Override public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                                  WebSocketHandler handler, Exception exception) {}
        }); // Default same-origin check; no wildcard public WebSocket endpoint.
    }
}
