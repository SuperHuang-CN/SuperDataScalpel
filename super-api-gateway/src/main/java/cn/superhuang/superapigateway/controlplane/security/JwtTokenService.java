package cn.superhuang.superapigateway.controlplane.security;

import cn.superhuang.superapigateway.configuration.SuperApiGatewayProperties;
import cn.superhuang.superapigateway.controlplane.web.response.AuthResponse;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtTokenService {

    private final SuperApiGatewayProperties.Admin properties;
    private final byte[] secret;

    public JwtTokenService(SuperApiGatewayProperties properties) {
        this.properties = properties.admin();
        this.secret = properties.admin().jwtSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("SUPER_API_GATEWAY_JWT_SECRET must contain at least 32 bytes");
        }
    }

    public AuthResponse issue(String username) {
        try {
            Instant now = Instant.now();
            Instant expiresAt = now.plus(properties.jwtTtl());
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(properties.jwtIssuer())
                    .subject(username)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(expiresAt))
                    .claim("role", "ADMIN")
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(secret));
            return new AuthResponse(jwt.serialize(), "Bearer", expiresAt, username);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to issue management token", exception);
        }
    }

    public boolean validate(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            return JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm())
                    && jwt.verify(new MACVerifier(secret))
                    && properties.jwtIssuer().equals(claims.getIssuer())
                    && properties.username().equals(claims.getSubject())
                    && claims.getExpirationTime() != null
                    && claims.getExpirationTime().toInstant().isAfter(Instant.now());
        } catch (Exception exception) {
            return false;
        }
    }
}
