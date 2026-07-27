package cn.superhuang.data.scalpel.business.datasource.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/** AES-GCM storage boundary for data-source credentials. */
@Component
public class DataSourceCredentialCipher {

    private static final String PREFIX = "v1:";
    private static final byte[] AAD = "datascalpel-data-source-credentials-v1".getBytes(StandardCharsets.UTF_8);
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final String encodedKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public DataSourceCredentialCipher(DataSourceSecurityProperties properties) {
        this.encodedKey = properties.credentialKey();
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            throw new IllegalArgumentException("数据源凭据不能为空");
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            byte[] encrypted = cipher(Cipher.ENCRYPT_MODE, iv).doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException exception) {
            throw unavailable("无法加密数据源凭据", exception);
        }
    }

    public String decrypt(String ciphertext) {
        try {
            if (ciphertext == null || !ciphertext.startsWith(PREFIX)) {
                throw new GeneralSecurityException("unsupported ciphertext version");
            }
            byte[] payload = Base64.getDecoder().decode(ciphertext.substring(PREFIX.length()));
            if (payload.length <= IV_BYTES) {
                throw new GeneralSecurityException("invalid ciphertext");
            }
            byte[] iv = Arrays.copyOfRange(payload, 0, IV_BYTES);
            byte[] encrypted = Arrays.copyOfRange(payload, IV_BYTES, payload.length);
            return new String(cipher(Cipher.DECRYPT_MODE, iv).doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw unavailable("无法解密数据源凭据", exception);
        }
    }

    private Cipher cipher(int mode, byte[] iv) throws GeneralSecurityException {
        byte[] key;
        try {
            key = encodedKey == null || encodedKey.isBlank() ? new byte[0] : Base64.getDecoder().decode(encodedKey);
        } catch (IllegalArgumentException exception) {
            throw new GeneralSecurityException("credential key is not base64", exception);
        }
        if (key.length != 16 && key.length != 24 && key.length != 32) {
            throw new GeneralSecurityException("credential key must contain 16, 24 or 32 bytes");
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
        cipher.updateAAD(AAD);
        return cipher;
    }

    private static ResponseStatusException unavailable(String message, Exception cause) {
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                message + "，请检查 data-scalpel.datasource.credential-key 配置",
                cause
        );
    }
}
