package movie.security;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Always populates a {@link MoviePrincipal} — a real one from a valid
 * Bearer token, or {@link MoviePrincipal#GUEST} when there's no token at
 * all — mirroring the Python reference's {@code get_current_principal}
 * ("no token is a guest, not a 401"). An actually-invalid/expired token
 * is rejected with 401 immediately, regardless of endpoint: a
 * simplification of the reference, where only routes that declare a
 * Principal dependency validate the token at all (a garbage token sent
 * to a route that never looks at identity is silently ignored there).
 * Rejecting it everywhere is more consistent/predictable and differs
 * from the reference only in that one edge case.
 */
@Component
public class JwtPrincipalFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtPrincipalResolver resolver;

    public JwtPrincipalFilter(JwtPrincipalResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        MoviePrincipal principal;
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            try {
                principal = resolver.resolve(header.substring(BEARER_PREFIX.length()));
            } catch (InvalidTokenException e) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, e.getMessage());
                return;
            }
        } else {
            principal = MoviePrincipal.GUEST;
        }

        SecurityContextHolder.getContext().setAuthentication(toAuthentication(principal));
        filterChain.doFilter(request, response);
    }

    private static Authentication toAuthentication(MoviePrincipal principal) {
        List<SimpleGrantedAuthority> authorities = principal.isGuest()
                ? List.of()
                : List.of(new SimpleGrantedAuthority("ROLE_" + principal.type().getValue().toUpperCase()));
        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }
}
