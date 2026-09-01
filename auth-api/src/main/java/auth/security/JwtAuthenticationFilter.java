package auth.security;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import auth.service.TokenService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Reads a {@code Bearer} token, validates it (signature, expiry, and
 * revocation via {@link TokenService}), and populates the
 * {@link SecurityContextHolder} with an {@link AuthPrincipal}. A missing
 * or invalid token simply leaves the context empty — downstream
 * authorization rules reject the request as unauthenticated rather than
 * this filter raising an error itself.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenService tokenService;

    public JwtAuthenticationFilter(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            try {
                Claims claims = tokenService.validate(header.substring(BEARER_PREFIX.length()));
                SecurityContextHolder.getContext().setAuthentication(toAuthentication(claims));
            } catch (InvalidTokenException e) {
                // Leave the context empty; see class javadoc.
            }
        }
        filterChain.doFilter(request, response);
    }

    private static Authentication toAuthentication(Claims claims) {
        String userType = (String) claims.get("user_type");
        AuthPrincipal principal = new AuthPrincipal(
                UUID.fromString(claims.getSubject()),
                (String) claims.get("email"),
                userType,
                claims.getId(),
                claims.getExpiration().toInstant());
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + userType.toUpperCase()));
        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }
}
