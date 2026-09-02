package auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

// UserDetailsServiceAutoConfiguration excluded: auth doesn't use Spring
// Security's built-in authentication providers (login is handled
// directly against our own User/PasswordEncoder; requests are
// authenticated by JwtAuthenticationFilter), so the default in-memory
// user + generated-password log line would just be noise.
//
// EnableScheduling powers RevokedTokenCleanupTask's periodic purge.
@EnableScheduling
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
