package cn.superhuang.data.scalpel.business.service.consumer.credential.web.response;

public record ApiConsumerCredentialSecretResponse(
        ApiConsumerCredentialResponse credential,
        String secret
) {
}
