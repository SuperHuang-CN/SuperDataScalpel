package cn.superhuang.superapigateway.controlplane.service;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Component
public class ApiKeySecretService {

    private final SecureRandom random = new SecureRandom();

    public Secret generate() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String plaintext = "sag_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return describe(plaintext);
    }

    public Secret describe(String plaintext) {
        String hash = sha256(plaintext);
        return new Secret(
                plaintext,
                hash,
                plaintext.substring(0, Math.min(8, plaintext.length())),
                plaintext.substring(Math.max(0, plaintext.length() - 4))
        );
    }

    public String sha256(String plaintext) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(plaintext.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record Secret(String plaintext, String hash, String prefix, String lastFour) {
    }
}
