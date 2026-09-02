package movie.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import movie.ratelimit.RateLimitFilter;
import movie.security.JwtPrincipalFilter;

/**
 * Much more open than auth-api's: movie-api is mostly a public catalog
 * plus guest-allowed reservations, so nothing is gated at the HTTP
 * level — admin-only writes are enforced per-method via
 * {@code @PreAuthorize("hasRole('ADMIN')")} instead. "Must be a real
 * (non-guest) identity" (e.g. listing "my reservations") is enforced
 * imperatively in the controller/service, not declaratively here —
 * Spring Security's vocabulary has no "not-guest" concept, and the
 * Python reference's {@code require_authenticated} is likewise just
 * ordinary code, not a framework-level rule.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http, JwtPrincipalFilter jwtPrincipalFilter, RateLimitFilter rateLimitFilter)
            throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .anonymous(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(jwtPrincipalFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rateLimitFilter, JwtPrincipalFilter.class);
        return http.build();
    }
}
