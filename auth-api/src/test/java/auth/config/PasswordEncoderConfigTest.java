package auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordEncoderConfigTest {

    private final PasswordEncoder encoder = new PasswordEncoderConfig().passwordEncoder();

    @Test
    void hashesWithArgon2idAndVerifiesRoundTrip() {
        String hash = encoder.encode("correct horse battery staple");

        assertThat(hash).startsWith("$argon2id$");
        assertThat(encoder.matches("correct horse battery staple", hash)).isTrue();
        assertThat(encoder.matches("wrong password", hash)).isFalse();
    }
}
