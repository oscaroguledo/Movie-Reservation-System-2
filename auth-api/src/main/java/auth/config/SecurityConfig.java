package auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import auth.ratelimit.RateLimitFilter;
import auth.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http, RateLimitFilter rateLimitFilter, JwtAuthenticationFilter jwtAuthenticationFilter)
            throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                // Spring Security's anonymous filter would otherwise hand
                // out an "authenticated" placeholder for every request
                // with no token, defeating .authenticated() below.
                .anonymous(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // /error must stay open: sendError() (e.g. from
                        // AccessDeniedHandlerImpl on a 403) triggers an
                        // internal forward there, and OncePerRequestFilter
                        // skips ERROR dispatches by default — so
                        // JwtAuthenticationFilter never re-authenticates
                        // that forwarded request, and without this it gets
                        // rejected as unauthenticated, clobbering the
                        // original status with a 401.
                        .requestMatchers("/health", "/actuator/**", "/auth/register", "/auth/login", "/error")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(eh -> eh.authenticationEntryPoint((request, response, authException) ->
                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rateLimitFilter, JwtAuthenticationFilter.class);
        return http.build();
    }
}
