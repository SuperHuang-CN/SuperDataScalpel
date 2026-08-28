package cn.superhuang.data.scalpel.engine.security;

import cn.superhuang.data.scalpel.web.error.ProblemDetailWriter;
import cn.superhuang.data.scalpel.web.error.ProblemType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration(proxyBeanMethods = false)
public class EngineSecurityConfiguration {

    @Bean
    SecurityFilterChain engineSecurityFilterChain(
            HttpSecurity http,
            ManagementTokenFilter managementTokenFilter,
            EngineServiceAccessPolicyFilter engineServiceAccessPolicyFilter,
            ProblemDetailWriter problemDetailWriter
    ) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers("/modern-ui", "/modern-ui/**").permitAll()
                        .requestMatchers("/interface-ui/**").permitAll()
                        .requestMatchers("/runtime/v1/services/**").permitAll()
                        .requestMatchers("/internal/v1/**").hasAuthority("engine.manage")
                        .anyRequest().denyAll())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                problemDetailWriter.write(request, response, ProblemType.AUTHENTICATION_REQUIRED,
                                        "需要有效的服务引擎管理令牌"))
                        .accessDeniedHandler((request, response, exception) ->
                                problemDetailWriter.write(request, response, ProblemType.ACCESS_DENIED,
                                        "当前请求无权访问该资源")))
                .addFilterBefore(managementTokenFilter, AnonymousAuthenticationFilter.class)
                .addFilterAfter(engineServiceAccessPolicyFilter, ManagementTokenFilter.class);
        return http.build();
    }
}
