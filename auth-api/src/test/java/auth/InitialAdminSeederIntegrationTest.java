package auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import auth.repository.UserRepository;
import auth.service.InitialAdminSeeder;

/**
 * InitialAdminSeeder is an {@code ApplicationRunner} — setting the two
 * required properties before context startup is enough to exercise it
 * end to end. Logging in through the real HTTP stack (not just checking
 * the Postgres row) proves the seeded account is actually usable: right
 * email, right password hash, right role.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "auth.rate-limit.max-requests=1000",
    "auth.initial-admin.email=seed-admin@example.com",
    "auth.initial-admin.password=Str0ngPass!1",
    "auth.initial-admin.first-name=Seeded",
    "auth.initial-admin.last-name=Admin"
})
class InitialAdminSeederIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private InitialAdminSeeder seeder;

    @Autowired
    private UserRepository userRepository;

    @Test
    void theSeededAdminCanLogInAndCarriesTheAdminRole() {
        ResponseEntity<Map> login = rest.postForEntity(
                "/auth/login",
                Map.of("email", "seed-admin@example.com", "password", "Str0ngPass!1"),
                Map.class);

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        String token = (String) login.getBody().get("accessToken");
        assertThat(token).isNotBlank();

        ResponseEntity<Map> me = rest.exchange(
                RequestEntity.get("/users/me").header("Authorization", "Bearer " + token).build(), Map.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody().get("userType")).isEqualTo("admin");
        assertThat(me.getBody().get("firstName")).isEqualTo("Seeded");
    }

    @Test
    void rerunningTheSeederAfterItAlreadyRanIsANoOp() {
        long before = userRepository.count();

        // The ApplicationRunner already ran once at context startup for
        // this class; re-running it (the same code path a restart would
        // take) must not fail or create a duplicate row.
        seeder.run(null);

        assertThat(userRepository.count()).isEqualTo(before);
    }
}
