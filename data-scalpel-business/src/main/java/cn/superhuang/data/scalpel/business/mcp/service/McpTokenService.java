package cn.superhuang.data.scalpel.business.mcp.service;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class McpTokenService {
    private final SecureRandom secureRandom = new SecureRandom();
    public IssuedToken issue() {
        byte[] secret = new byte[32];
        secureRandom.nextBytes(secret);
        String plaintext = "dsmcp_" + Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        return new IssuedToken(plaintext, digest(plaintext), plaintext.substring(0, 12) + "…" + plaintext.substring(plaintext.length() - 4));
    }
    public boolean matches(String plaintext, String expectedDigest) {
        if (plaintext == null || expectedDigest == null) return false;
        return MessageDigest.isEqual(digest(plaintext).getBytes(StandardCharsets.US_ASCII), expectedDigest.getBytes(StandardCharsets.US_ASCII));
    }
    public String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
    public record IssuedToken(String plaintext, String digest, String hint) {}
}
