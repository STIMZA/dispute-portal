package com.example.disputeportal.security;

import com.example.disputeportal.config.ApiProperties;
import com.example.disputeportal.service.ApiClientService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * All security rules, as two separate filter chains.
 *
 * Order 1 handles /api/** (machine clients, API key, stateless). Order 2 handles everything else (people using
 * the web pages, form login with a session). Spring tries the chains in order and uses the first whose
 * matcher fits the request.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * Machine API: stateless, authenticated by API key only. No sessions, cookies or login form, so there is
     * nothing for CSRF to protect (a browser has no credential to send automatically). CORS is not enabled,
     * so browsers on other sites can't call it either.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, ApiClientService apiClients,
                                                      LoginAttemptService attempts, ApiProperties properties,
                                                      ApiErrorWriter errors) throws Exception {
        http
            .securityMatcher("/api/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Least privilege: each endpoint needs its own scope, and anything else under /api is refused.
                .requestMatchers("/api/v1/transactions/**").hasAuthority(ApiScopes.authority(ApiScopes.TRANSACTIONS_LOAD))
                .requestMatchers("/api/v1/customers/**").hasAuthority(ApiScopes.authority(ApiScopes.CUSTOMERS_CREATE))
                .anyRequest().denyAll())
            .addFilterBefore(new ApiKeyAuthenticationFilter(apiClients, attempts, properties, errors),
                    UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(handling -> handling
                .authenticationEntryPoint((request, response, ex) -> {
                    response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
                    errors.write(response, 401, "Authentication required");
                })
                .accessDeniedHandler((request, response, ex) -> errors.write(response, 403, "Forbidden")))
            .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'")));
        return http.build();
    }

    /** The customer and admin web pages. */
    @Bean
    @Order(2)
    public SecurityFilterChain webSecurityFilterChain(HttpSecurity http, LoginAttemptService loginAttempts)
            throws Exception {
        http
            // CSRF protection stays on (the default); every form includes the token via th:action.
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/css/**", "/error", "/error/**", "/actuator/health/**").permitAll()
                // Authorization is enforced here and again on the admin service (@PreAuthorize).
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/transactions/**", "/disputes/**").hasRole("CUSTOMER")
                // Notifications belong to whoever is signed in, customer or admin (each sees only their own).
                .requestMatchers("/notifications/**").authenticated()
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login")
                .successHandler(new RoleBasedSuccessHandler(loginAttempts))
                .failureHandler(new LoginFailureHandler(loginAttempts))
                .permitAll())
            // Refuses login attempts from a client that has failed too often (see LoginAttemptService).
            .addFilterBefore(new LoginThrottleFilter(loginAttempts), UsernamePasswordAuthenticationFilter.class)
            .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
            .sessionManagement(session -> session.sessionFixation(fixation -> fixation.migrateSession()))
            .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(
                "default-src 'self'; style-src 'self'; frame-ancestors 'none'; form-action 'self'")));
        return http.build();
    }

    /**
     * BCrypt: salted and deliberately slow, so stolen password hashes are hard to crack. Used at login and when creating accounts.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
