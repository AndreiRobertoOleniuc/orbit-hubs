package com.orbit.security;

import com.orbit.app.auth.UserProvisioningService;

import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Resource-server security for Orbit.
 *
 * <p>
 * Configures OAuth2 JWT validation for Clerk session tokens, route-level access
 * rules, CORS for the configured frontend origin, and just-in-time user
 * provisioning after successful authentication.
 */
@Configuration
@EnableWebSecurity
class ClerkSecurityConfig {

    @Bean
    SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http,
            UserProvisioningService provisioningService) throws Exception {

        http
                .securityMatcher("/api/v1/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(cors -> {
                })
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(clerkJwtAuthenticationConverter())))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(CorsUtils::isPreFlightRequest).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/search").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/search/rationale/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/board").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/requests/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/entities/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterAfter(new UserProvisioningFilter(provisioningService), BearerTokenAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    JwtAuthenticationConverter clerkJwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setPrincipalClaimName("sub");
        converter.setJwtGrantedAuthoritiesConverter(jwt -> Collections.emptyList());
        return converter;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${clerk.azp-origin}") String azpOrigin) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(azpOrigin));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/v1/**", configuration);
        return source;
    }

}
