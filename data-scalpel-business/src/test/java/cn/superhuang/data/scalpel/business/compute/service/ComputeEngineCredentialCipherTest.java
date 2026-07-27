package cn.superhuang.data.scalpel.business.compute.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComputeEngineCredentialCipherTest {

    @Test
    void encryptsWithRandomAesGcmPayloadAndDecrypts() {
        String key = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());
        ComputeEngineCredentialCipher cipher = cipher(key);

        String first = cipher.encrypt("dispatcher-secret");
        String second = cipher.encrypt("dispatcher-secret");

        assertTrue(first.startsWith("v1:"));
        assertFalse(first.contains("dispatcher-secret"));
        assertFalse(first.equals(second));
        assertEquals("dispatcher-secret", cipher.decrypt(first));
        assertEquals("dispatcher-secret", cipher.decrypt(second));
    }

    @Test
    void refusesOperationsWithoutValidCredentialKey() {
        ResponseStatusException missing = assertThrows(ResponseStatusException.class, () -> cipher("").encrypt("secret"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, missing.getStatusCode());
        ResponseStatusException invalid = assertThrows(ResponseStatusException.class, () -> cipher("not-base64").encrypt("secret"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, invalid.getStatusCode());
    }

    private static ComputeEngineCredentialCipher cipher(String key) {
        return new ComputeEngineCredentialCipher(new ComputeEngineProperties(
                key, Duration.ofSeconds(1), Duration.ofSeconds(1)
        ));
    }
}
