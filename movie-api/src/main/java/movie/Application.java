package movie;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

// Excluded for the same reason as auth-api's Application: movie-api never
// issues or checks credentials via Spring Security's built-in providers —
// it only ever validates a JWT auth-api already issued (see JwtPrincipalFilter,
// added alongside SecurityConfig later in the port).
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
