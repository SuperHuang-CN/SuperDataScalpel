package cn.superhuang.data.scalpel.admin.security;

import cn.superhuang.data.scalpel.business.system.access.service.SystemAccessService;
import cn.superhuang.data.scalpel.business.mcp.security.McpTokenAuthenticationFilter;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.authentication.AbstractAuthenticationToken;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfiguration {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(SystemAccessService accessService) {
        return username -> accessService.findAuthenticationUser(username)
                .map(user -> {
                    List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + user.roleCode()));
                    user.permissionCodes().forEach(code -> authorities.add(new SimpleGrantedAuthority(code)));
                    return User.withUsername(user.username())
                            .password(user.passwordHash())
                            .authorities(authorities)
                            .build();
                })
                .orElseThrow(() -> new UsernameNotFoundException("用户名或密码错误"));
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    JwtEncoder jwtEncoder(SecurityProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey(properties)));
    }

    @Bean
    JwtDecoder jwtDecoder(SecurityProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey(properties))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.jwt().issuer()));
        return decoder;
    }

    @Bean
    Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter() {
        return new DataScalpelJwtAuthenticationConverter();
    }

    private static final class DataScalpelJwtAuthenticationConverter
            implements Converter<Jwt, AbstractAuthenticationToken> {

        @Override
        public AbstractAuthenticationToken convert(Jwt jwt) {
            List<SimpleGrantedAuthority> authorities = new ArrayList<>();
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles != null) {
                roles.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
            }
            List<String> permissions = jwt.getClaimAsStringList("permissions");
            if (permissions != null) {
                permissions.forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission)));
            }
            return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
        }
    }

    @Bean
    @Order(1)
    SecurityFilterChain mcpSecurityFilterChain(HttpSecurity http,
                                               McpTokenAuthenticationFilter tokenAuthenticationFilter,
                                               RestAuthenticationEntryPoint authenticationEntryPoint,
                                               RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .securityMatcher("/mcp/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().hasAuthority("mcp.invoke"))
                .addFilterBefore(tokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain systemMcpSecurityFilterChain(HttpSecurity http,
            cn.superhuang.data.scalpel.business.systemmcp.service.SystemMcpTokenService tokens,
            cn.superhuang.data.scalpel.web.error.ProblemDetailWriter errors,
            RestAuthenticationEntryPoint entryPoint, RestAccessDeniedHandler denied) throws Exception {
        http.securityMatcher(request -> {
                    String path = request.getRequestURI().substring(request.getContextPath().length());
                    String header = request.getHeader("Authorization");
                    return path.equals("/system-mcp") || header != null && header.startsWith("Bearer dssmcp_");
                })
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .addFilterBefore(new cn.superhuang.data.scalpel.business.systemmcp.security.SystemMcpTokenFilter(tokens, errors), UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(entryPoint).accessDeniedHandler(denied));
        return http.build();
    }

    @Bean
    @Order(3)
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter,
                                            RestAuthenticationEntryPoint authenticationEntryPoint,
                                            RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/api/v1/asset-portal/**").permitAll()
                        .requestMatchers(
                                "/api/v1/auth/login",
                                "/actuator/health/**",
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**")
                        .permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }

    private SecretKey secretKey(SecurityProperties properties) {
        return new SecretKeySpec(
                properties.jwt().secret().getBytes(StandardCharsets.UTF_8),
                "HmacSHA256");
    }
}
