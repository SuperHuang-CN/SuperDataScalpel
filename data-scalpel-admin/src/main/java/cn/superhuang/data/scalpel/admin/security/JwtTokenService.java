package cn.superhuang.data.scalpel.admin.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class JwtTokenService {

    private final JwtEncoder jwtEncoder;
    private final SecurityProperties securityProperties;

    public JwtTokenService(JwtEncoder jwtEncoder, SecurityProperties securityProperties) {
        this.jwtEncoder = jwtEncoder;
        this.securityProperties = securityProperties;
    }

    public IssuedToken issue(Authentication authentication) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(securityProperties.jwt().timeToLive());
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring(5))
                .toList();
        List<String> permissions = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> !authority.startsWith("ROLE_"))
                .sorted()
                .toList();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(securityProperties.jwt().issuer())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(authentication.getName())
                .claim("roles", roles)
                .claim("permissions", permissions)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, expiresAt);
    }

    public record IssuedToken(String value, Instant expiresAt) {
    }
}
