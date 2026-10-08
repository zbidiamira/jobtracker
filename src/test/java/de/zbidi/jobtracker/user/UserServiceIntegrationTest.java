package de.zbidi.jobtracker.user;

import de.zbidi.jobtracker.PostgresTestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Real BCrypt, real Postgres: what actually ends up in the password_hash column.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresTestcontainersConfiguration.class, UserService.class, UserServiceIntegrationTest.BCrypt.class})
class UserServiceIntegrationTest {

	private static final String PASSWORD = "s3cret-pass";

	@TestConfiguration(proxyBeanMethods = false)
	static class BCrypt {

		@Bean
		PasswordEncoder passwordEncoder() {
			return new BCryptPasswordEncoder();
		}

	}

	@Autowired
	UserService userService;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void passwordIsStoredAsBCryptHash() {
		Long id = userService.register("alice@example.com", PASSWORD).getId();

		// straight from the column, bypassing JPA
		String stored = jdbc.queryForObject("SELECT password_hash FROM app_user WHERE id = ?", String.class, id);

		assertThat(stored).startsWith("$2").hasSize(60).doesNotContain(PASSWORD);
		assertThat(passwordEncoder.matches(PASSWORD, stored)).isTrue();
		assertThat(passwordEncoder.matches("wrong-password", stored)).isFalse();
	}

	@Test
	void samePasswordGivesDifferentHashesBecauseOfTheSalt() {
		String alice = userService.register("alice@example.com", PASSWORD).getPasswordHash();
		String bob = userService.register("bob@example.com", PASSWORD).getPasswordHash();

		assertThat(alice).isNotEqualTo(bob);
	}

	@Test
	void duplicateEmailWithDifferentCaseIsRejected() {
		userService.register("alice@example.com", PASSWORD);

		assertThatExceptionOfType(EmailAlreadyRegisteredException.class)
				.isThrownBy(() -> userService.register("Alice@Example.com", "another-pass"));
	}

}
