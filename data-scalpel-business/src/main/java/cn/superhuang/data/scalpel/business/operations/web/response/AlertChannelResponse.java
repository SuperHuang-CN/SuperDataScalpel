package cn.superhuang.data.scalpel.business.operations.web.response;
import cn.superhuang.data.scalpel.business.operations.domain.AlertChannel;
import java.util.UUID;
public record AlertChannelResponse(UUID id, String name, String url, boolean enabled, long configurationVersion,
                                   boolean bearerTokenConfigured, boolean hmacSecretConfigured) {
    public static AlertChannelResponse from(AlertChannel c) {
        return new AlertChannelResponse(c.getId(), c.getName(), c.getUrl(), c.getEnabled(), c.getConfigurationVersion(),
                c.getBearerCiphertext() != null, c.getHmacCiphertext() != null);
    }
}
