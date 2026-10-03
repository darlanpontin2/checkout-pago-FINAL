package br.com.rony.ecommerce.infrastructure.configuration.checkout;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;
import java.util.*;

@Configuration
@EnableScheduling
public class CheckoutSecurityConfiguration {
    @Bean
    JwtDecoder checkoutJwtDecoder(
            @Value("${checkout.security.jwk-set-uri}") String jwkSetUri,
            @Value("${checkout.security.issuer}") String issuer,
            @Value("${checkout.security.audience}") String audience) {
        if (!jwkSetUri.startsWith("https://"))
            throw new IllegalArgumentException("JWKS deve utilizar HTTPS.");
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> jwt.getAudience().contains(audience)
            ? OAuth2TokenValidatorResult.success()
            : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Audiência inválida.", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(issuer), audienceValidator));
        return decoder;
    }

    @Bean
    CorsConfigurationSource checkoutCors(
            @Value("${checkout.security.allowed-origins}") List<String> origins) {
        if (origins.isEmpty() || origins.stream().anyMatch(
                origin -> origin.contains("*") || !origin.startsWith("https://")))
            throw new IllegalArgumentException("Origens HTTPS explícitas são obrigatórias.");
        org.springframework.web.cors.CorsConfiguration config = new org.springframework.web.cors.CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
        config.setExposedHeaders(List.of("Retry-After"));
        config.setAllowCredentials(false);
        config.setMaxAge(600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    SecurityFilterChain checkoutSecurity(HttpSecurity http,
            CorsConfigurationSource checkoutCors, ObjectMapper mapper) throws Exception {
        http.requiresChannel(channel -> channel.anyRequest().requiresSecure())
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(checkoutCors))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/webhook/pagamento").permitAll()
                .requestMatchers("/api/checkout/**").hasAuthority("SCOPE_checkout")
                .requestMatchers(HttpMethod.GET, "/products/**").permitAll()
                .requestMatchers("/products/**").hasAuthority("SCOPE_catalog:write")
                .anyRequest().denyAll())
            .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> {}))
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) -> {
                    response.setStatus(401);
                    response.setContentType("application/json");
                    mapper.writeValue(response.getOutputStream(), Map.of(
                        "sucesso", false, "erro", "NAO_AUTENTICADO", "mensagem", "Autenticação obrigatória."));
                })
                .accessDeniedHandler((request, response, exception) -> {
                    response.setStatus(403);
                    response.setContentType("application/json");
                    mapper.writeValue(response.getOutputStream(), Map.of(
                        "sucesso", false, "erro", "ACESSO_NEGADO", "mensagem", "Você não possui acesso a este recurso."));
                }))
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                .frameOptions(frame -> frame.deny())
                .contentTypeOptions(content -> {})
                .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000)));
        return http.build();
    }
}
