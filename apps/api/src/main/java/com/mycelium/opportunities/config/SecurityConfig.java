package com.mycelium.opportunities.config;

import com.mycelium.opportunities.auth.AccountService;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.*;

@Configuration
public class SecurityConfig {
  @Bean
  PasswordEncoder passwords() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  SecurityFilterChain security(HttpSecurity http, AccountService accounts) throws Exception {
    var csrf = new HttpSessionCsrfTokenRepository();
    http.userDetailsService(accounts)
        .csrf(c -> c.csrfTokenRepository(csrf))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/api/v1/auth/csrf",
                        "/api/v1/auth/register",
                        "/api/v1/auth/login",
                        "/actuator/health")
                    .permitAll()
                    .requestMatchers(
                        "/api/v1/admin/**",
                        "/api/v1/openapi/**",
                        "/api/v1/docs/**",
                        "/swagger-ui/**")
                    .hasRole("ADMIN")
                    .requestMatchers(
                        org.springframework.http.HttpMethod.GET,
                        "/api/v1/opportunities/**",
                        "/api/v1/sources")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .formLogin(
            f ->
                f.loginProcessingUrl("/api/v1/auth/login")
                    .usernameParameter("email")
                    .successHandler((q, s, a) -> s.setStatus(204))
                    .failureHandler((q, s, e) -> s.sendError(401, "Invalid credentials")))
        .logout(
            l ->
                l.logoutUrl("/api/v1/auth/logout")
                    .logoutSuccessHandler((q, s, a) -> s.setStatus(204))
                    .deleteCookies("JSESSIONID"))
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint((q, s, x) -> s.sendError(401))
                    .accessDeniedHandler((q, s, x) -> s.sendError(403)))
        .requestCache(c -> c.disable());
    return http.build();
  }
}
