package com.chifaa.backend.security;

import com.chifaa.backend.auth.LocalAuthentication;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import java.time.Clock;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class SecurityConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    Clock authClock() { return Clock.systemUTC(); }

    @Bean
    SecurityFilterChain api(HttpSecurity http, ObjectProvider<LocalAuthentication> auth, Environment environment) throws Exception {
        boolean dev = environment.acceptsProfiles(Profiles.of("dev"));
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e.authenticationEntryPoint((req, res, failure) -> res.setStatus(401))
                        .accessDeniedHandler((req, res, failure) -> res.setStatus(403)))
                .authorizeHttpRequests(rules -> rules
                        .requestMatchers(HttpMethod.GET, "/api/health", "/api/organisations", "/api/organisations/**",
                                "/v3/api-docs", "/v3/api-docs.yaml", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers("/api/chat/**").hasRole("USER")
                        .requestMatchers("/api/admin/safety-events/**").hasAnyRole("SAFETY_REVIEWER", "ADMIN")
                        .requestMatchers("/api/admin/organisations/**").hasAnyRole("CONTENT_TEAM", "ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(new OncePerRequestFilter() {
                    @Override
                    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                            throws ServletException, IOException {
                        if (!dev && !request.isSecure()) {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            return;
                        }
                        String header = request.getHeader("Authorization");
                        var provider = auth.getIfAvailable();
                        if (provider != null && header != null && header.startsWith("Bearer ")) {
                            var identity = provider.resolve(header.substring(7));
                            if (identity != null) {
                                var principal = UsernamePasswordAuthenticationToken.authenticated(identity, null,
                                        List.of(new SimpleGrantedAuthority("ROLE_" + identity.role())));
                                SecurityContextHolder.getContext().setAuthentication(principal);
                            }
                        }
                        chain.doFilter(request, response);
                    }
                }, AnonymousAuthenticationFilter.class).build();
    }
}
