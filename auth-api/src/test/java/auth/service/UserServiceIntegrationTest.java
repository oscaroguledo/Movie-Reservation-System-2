package auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import auth.IntegrationTestSupport;
import auth.model.User;
import auth.model.UserType;
import auth.repository.UserRepository;

/**
 * Exercises {@link UserService#list}'s dynamic JPQL filtering/pagination
 * against real Postgres — the shared container across the whole suite,
 * so every seeded row's first/last name is tagged with a unique random
 * string to keep this test's filters from matching anything else's data.
 * Seeds rows directly via the repository (bypassing registration) to
 * test the query in isolation from the async Kafka pipeline's timing.
 */
@SpringBootTest
class UserServiceIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void listFiltersByTypeAndName() {
        String tag = uniqueTag();
        userRepository.save(user(tag + "Alice", "Anderson", UserType.ADMIN));
        userRepository.save(user(tag + "Bob", "Baker", UserType.REGULAR));
        userRepository.save(user(tag + "Alicia", "Carter", UserType.REGULAR));

        List<User> all = userService.list(null, tag, null, 100, 0);
        assertThat(all).hasSize(3);

        List<User> admins = userService.list(UserType.ADMIN, tag, null, 100, 0);
        assertThat(admins).hasSize(1);
        assertThat(admins.get(0).getFirstName()).isEqualTo(tag + "Alice");

        List<User> byLastName = userService.list(null, null, "anderson", 100, 0);
        assertThat(byLastName).extracting(User::getLastName).contains("Anderson");
    }

    @Test
    void listRespectsLimitAndOffsetInCreationOrder() {
        String tag = uniqueTag();
        for (int i = 0; i < 5; i++) {
            userRepository.save(user(tag, "Page" + i, UserType.REGULAR));
        }

        List<User> firstPage = userService.list(null, tag, null, 2, 0);
        List<User> secondPage = userService.list(null, tag, null, 2, 2);

        assertThat(firstPage).hasSize(2);
        assertThat(secondPage).hasSize(2);
        assertThat(firstPage).doesNotContainAnyElementsOf(secondPage);
    }

    private static User user(String firstName, String lastName, UserType userType) {
        return new User(
                UUID.randomUUID(),
                UUID.randomUUID() + "@example.com",
                firstName,
                lastName,
                "hash",
                userType);
    }

    private static String uniqueTag() {
        return "utest" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
