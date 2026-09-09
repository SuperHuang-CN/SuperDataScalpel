package cn.superhuang.data.scalpel.business.systemmcp.config;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.time.Duration;
@Component
@ConfigurationProperties(prefix = "data-scalpel.system-mcp")
public class SystemMcpProperties {
    private String publicBaseUrl = "";
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration invokeTimeout = Duration.ofSeconds(60);
    private int concurrency = 8;
    private int maxRequestBytes = 2 * 1024 * 1024;
    private int maxResponseBytes = 1024 * 1024;
    private Duration auditRetention = Duration.ofDays(30);
    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }
    public void setPublicBaseUrl(String v) {
        publicBaseUrl = v;
    }
    public Duration getConnectTimeout() {
        return connectTimeout;
    }
    public void setConnectTimeout(Duration v) {
        connectTimeout = positive(v);
    }
    public Duration getInvokeTimeout() {
        return invokeTimeout;
    }
    public void setInvokeTimeout(Duration v) {
        invokeTimeout = positive(v);
    }
    public int getConcurrency() {
        return concurrency;
    }
    public void setConcurrency(int v) {
        if (v < 1 || v > 64) throw new IllegalArgumentException("并发必须为 1–64");
        concurrency = v;
    }
    public int getMaxRequestBytes() {
        return maxRequestBytes;
    }
    public void setMaxRequestBytes(int v) {
        if (v < 1024) throw new IllegalArgumentException("请求上限过小");
        maxRequestBytes = v;
    }
    public int getMaxResponseBytes() {
        return maxResponseBytes;
    }
    public void setMaxResponseBytes(int v) {
        if (v < 1024) throw new IllegalArgumentException("响应上限过小");
        maxResponseBytes = v;
    }
    public Duration getAuditRetention() {
        return auditRetention;
    }
    public void setAuditRetention(Duration v) {
        auditRetention = positive(v);
    }
    private static Duration positive(Duration v) {
        if (v == null || v.isNegative() || v.isZero()) throw new IllegalArgumentException("时间必须大于零");
        return v;
    }
}
