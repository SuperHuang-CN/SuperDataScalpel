package cn.superhuang.data.scalpel.business.operations.web.request;

import jakarta.validation.constraints.*;

public record SaveAlertChannelRequest(@NotBlank @Size(max=150) String name, @NotBlank @Size(max=2000) String url,
                                      boolean enabled, @Size(max=4000) String bearerToken,
                                      @Size(max=4000) String hmacSecret,
                                      Boolean clearBearerToken, Boolean clearHmacSecret) {
    public SaveAlertChannelRequest {
        clearBearerToken = Boolean.TRUE.equals(clearBearerToken);
        clearHmacSecret = Boolean.TRUE.equals(clearHmacSecret);
    }
}
