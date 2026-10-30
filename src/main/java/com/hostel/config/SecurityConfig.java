package com.hostel.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hostel.security.CustomUserDetailsService;
import com.hostel.security.JwtFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtFilter jwtFilter;
    private final CustomUserDetailsService userDetailsService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SecurityConfig(JwtFilter jwtFilter, CustomUserDetailsService userDetailsService) {
        this.jwtFilter = jwtFilter;
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin())
                        .contentTypeOptions(content -> {})
                        .xssProtection(xss -> {})
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
                )
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers(
                                "/",
                                "/index.html",
                                "/login.html",
                                "/change-password.html",
                                "/css/**",
                                "/js/**",
                                "/img/**",
                                "/favicon.ico",
                                "/error",
                                "/api/auth/login",
                                "/api/auth/logout"
                        ).permitAll()
                        // General authenticated
                        .requestMatchers("/api/auth/me", "/api/auth/change-password").authenticated()
                        // Student only
                        .requestMatchers("/api/student/**").hasRole("STUDENT")
                        // Warden and Admin
                        .requestMatchers("/api/warden/**").hasAnyRole("WARDEN", "ADMIN")
                        // Admin only
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // Dashboard HTML files can be accessed via browser; JS auth guard will enforce RBAC
                        .requestMatchers(
                                "/student/**",
                                "/warden/**",
                                "/admin/**",
                                "/security/**",
                                "/worker/**",
                                "/maintenance/**",
                                "/mess/**",
                                "/vending/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            Map<String, Object> error = new HashMap<>();
                            error.put("timestamp", LocalDateTime.now().toString());
                            error.put("status", HttpServletResponse.SC_UNAUTHORIZED);
                            error.put("error", "Unauthorized");
                            error.put("message", "Full authentication is required to access this resource");
                            objectMapper.writeValue(response.getOutputStream(), error);
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            Map<String, Object> error = new HashMap<>();
                            error.put("timestamp", LocalDateTime.now().toString());
                            error.put("status", HttpServletResponse.SC_FORBIDDEN);
                            error.put("error", "Forbidden");
                            error.put("message", "You do not have permission to access this resource");
                            objectMapper.writeValue(response.getOutputStream(), error);
                        })
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
