package tn.chifaa.auth;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
class SecurityConfig {
    @Bean java.time.Clock clock() { return java.time.Clock.systemUTC(); }
    @org.springframework.core.annotation.Order(1)
    @Bean SecurityFilterChain patientApi(HttpSecurity http, Sessions sessions) throws Exception {
        return http.securityMatcher("/api/auth/**","/api/users/me","/api/consents/**","/api/health/**","/api/data/**","/api/kb","/api/kb/**","/api/chat/**")
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(e -> e.authenticationEntryPoint((req,res,ex) -> res.setStatus(401)))
            .authorizeHttpRequests(a -> a.requestMatchers(HttpMethod.POST,"/api/auth/register","/api/auth/login","/api/auth/verify")
                .permitAll().anyRequest().hasRole("PATIENT"))
            .addFilterBefore(new OncePerRequestFilter() {
                @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                    throws IOException, ServletException {
                    String header = req.getHeader("Authorization");
                    if (header != null && header.startsWith("Bearer ")) {
                        var pid = sessions.resolve(header.substring(7));
                        if (pid != null) SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(pid,null,List.of(new SimpleGrantedAuthority("ROLE_PATIENT"))));
                    }
                    chain.doFilter(req,res);
                }
            }, UsernamePasswordAuthenticationFilter.class).build();
    }
}
