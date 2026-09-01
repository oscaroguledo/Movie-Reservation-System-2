package movie;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Placeholder entry point. The movie-api domain (catalog, showtimes,
 * reservations, payments, admin reporting) is a separate, larger follow-up
 * once auth-api is ported.
 */
@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
