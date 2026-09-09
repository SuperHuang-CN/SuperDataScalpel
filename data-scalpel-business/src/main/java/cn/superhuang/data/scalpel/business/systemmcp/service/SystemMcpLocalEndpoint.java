package cn.superhuang.data.scalpel.business.systemmcp.service;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import java.net.URI;
/** The destination is owned by the running application, never by a client or proxy header. */
@Component
public class SystemMcpLocalEndpoint {
    private final Environment environment;
    public SystemMcpLocalEndpoint(Environment environment) {
        this.environment = environment;
    }
    public URI base() {
        int port = Integer.parseInt(environment.getRequiredProperty("local.server.port"));
        String context = environment.getProperty("server.servlet.context-path", "");
        String scheme = environment.getProperty("server.ssl.enabled", Boolean.class, false) ? "https" : "http";
        return URI.create(scheme + "://127.0.0.1:" + port + context);
    }
}
