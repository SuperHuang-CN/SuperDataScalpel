package cn.superhuang.data.scalpel.business.dsh.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.time.Duration;
@org.springframework.validation.annotation.Validated
@Component
@ConfigurationProperties(prefix = "data-scalpel.dsh")
public class DshProperties {
    private boolean enabled = false;
    private String baseUrl = "http://127.0.0.1:13080";
    private String bridgeKey = "";
    private String credentialKey = "";
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration requestTimeout = Duration.ofSeconds(45);
    @jakarta.validation.constraints.Min(1)
    private int maxRequestBytes = 1048576;
    @jakarta.validation.constraints.Min(1)
    private int maxResponseBytes = 8388608;
    @jakarta.validation.constraints.Min(1)
    private int eventBufferBytes = 1048576;
    @jakarta.validation.constraints.AssertTrue(message="DSH timeouts must be positive")
    public boolean isTimeoutsValid() { return connectTimeout!=null&&!connectTimeout.isNegative()&&!connectTimeout.isZero()&&requestTimeout!=null&&!requestTimeout.isNegative()&&!requestTimeout.isZero(); }
    public boolean getEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String value) { baseUrl = value; }
    public String getBridgeKey() { return bridgeKey; }
    public void setBridgeKey(String value) { bridgeKey = value; }
    public String getCredentialKey() { return credentialKey; }
    public void setCredentialKey(String value) { credentialKey = value; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration value) { connectTimeout = value; }
    public Duration getRequestTimeout() { return requestTimeout; }
    public void setRequestTimeout(Duration value) { requestTimeout = value; }
    public int getMaxRequestBytes() { return maxRequestBytes; }
    public void setMaxRequestBytes(int value) { maxRequestBytes = value; }
    public int getMaxResponseBytes() { return maxResponseBytes; }
    public void setMaxResponseBytes(int value) { maxResponseBytes = value; }
    public int getEventBufferBytes() { return eventBufferBytes; }
    public void setEventBufferBytes(int value) { eventBufferBytes = value; }
}
