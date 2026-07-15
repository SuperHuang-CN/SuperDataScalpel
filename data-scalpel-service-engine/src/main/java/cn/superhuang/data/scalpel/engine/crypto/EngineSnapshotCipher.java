package cn.superhuang.data.scalpel.engine.crypto;

import cn.superhuang.data.scalpel.engine.config.EngineProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/** Encrypts source connection snapshots before they reach the Engine runtime database. */
@Component
public class EngineSnapshotCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 128;
    private final SecretKeySpec key;

    public EngineSnapshotCipher(EngineProperties properties) {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(properties.encryptionKey());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("DATASCALPEL_ENGINE_ENCRYPTION_KEY 必须是 Base64 编码的 AES 密钥", exception);
        }
        if (decoded.length != 16 && decoded.length != 24 && decoded.length != 32) {
            throw new IllegalStateException("DATASCALPEL_ENGINE_ENCRYPTION_KEY 必须解码为 16、24 或 32 字节");
        }
        this.key = new SecretKeySpec(decoded, "AES");
    }

    public String encrypt(String plainText) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(payload);
        } catch (Exception exception) {
            throw new IllegalStateException("无法加密 Engine 部署快照", exception);
        }
    }

    public String decrypt(String encryptedPayload) {
        try {
            byte[] payload = Base64.getDecoder().decode(encryptedPayload);
            if (payload.length <= IV_LENGTH) {
                throw new IllegalArgumentException("Encrypted payload is too short");
            }
            byte[] iv = java.util.Arrays.copyOfRange(payload, 0, IV_LENGTH);
            byte[] encrypted = java.util.Arrays.copyOfRange(payload, IV_LENGTH, payload.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("无法解密 Engine 部署快照", exception);
        }
    }
}
