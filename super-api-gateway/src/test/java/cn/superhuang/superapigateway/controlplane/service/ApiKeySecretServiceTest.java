package cn.superhuang.superapigateway.controlplane.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeySecretServiceTest {

    private final ApiKeySecretService service = new ApiKeySecretService();

    @Test
    void generatedSecretsAreRandomAndDescribedWithoutStoringPlaintext() {
        var first = service.generate();
        var second = service.generate();

        assertThat(first.plaintext()).startsWith("sag_").isNotEqualTo(second.plaintext());
        assertThat(first.hash()).hasSize(64).isEqualTo(service.sha256(first.plaintext()));
        assertThat(first.prefix()).isEqualTo(first.plaintext().substring(0, 8));
        assertThat(first.lastFour()).isEqualTo(
                first.plaintext().substring(first.plaintext().length() - 4)
        );
    }
}
