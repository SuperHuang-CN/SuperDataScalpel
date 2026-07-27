package cn.superhuang.data.scalpel.business.service.consumer.credential.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateApiConsumerCredentialRequest(
        @NotBlank @Size(max = 100) String name
) {
}
