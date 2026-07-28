package cn.superhuang.data.scalpel.business.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceEngineCredentialCipherTest {

    @Test
    void encryptsWithRandomAesGcmPayloadAndDecrypts() {
        ServiceEngineCredentialCipher cipher = cipher(
                Base64.getEncoder().encodeToString(
                        "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)
                )
        );

        String first = cipher.encrypt("engine-secret");
        String second = cipher.encrypt("engine-secret");

        assertTrue(first.startsWith("v1:"));
        assertFalse(first.contains("engine-secret"));
        assertFalse(first.equals(second));
        assertEquals("engine-secret", cipher.decrypt(first));
        assertEquals("engine-secret", cipher.decrypt(second));
    }

    @Test
    void refusesBlankTokensAndInvalidCredentialKeys() {
        ServiceEngineCredentialCipher valid = cipher(
                Base64.getEncoder().encodeToString(
                        "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)
                )
        );
        assertEquals(
                HttpStatus.BAD_REQUEST,
                assertThrows(ResponseStatusException.class, () -> valid.encrypt(" ")).getStatusCode()
        );
        assertEquals(
                HttpStatus.SERVICE_UNAVAILABLE,
                assertThrows(ResponseStatusException.class, () -> cipher("not-base64").encrypt("secret"))
                        .getStatusCode()
        );
    }

    @Test
    void rejectsCiphertextEncryptedWithAnotherKey() {
        String firstKey = Base64.getEncoder().encodeToString(
                "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)
        );
        String secondKey = Base64.getEncoder().encodeToString(
                "abcdef0123456789abcdef0123456789".getBytes(StandardCharsets.UTF_8)
        );
        String ciphertext = cipher(firstKey).encrypt("engine-secret");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> cipher(secondKey).decrypt(ciphertext)
        );
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
    }

    private static ServiceEngineCredentialCipher cipher(String key) {
        return new ServiceEngineCredentialCipher(new ServiceEngineSecurityProperties(key));
    }
}
