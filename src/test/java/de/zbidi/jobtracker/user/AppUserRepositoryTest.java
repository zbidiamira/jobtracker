package de.zbidi.jobtracker.user;

import de.zbidi.jobtracker.PostgresTestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestcontainersConfiguration.class)
class AppUserRepositoryTest {

	@Autowired
	AppUserRepository appUserRepository;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void findsUserByEmailIgnoringCase() {
		appUserRepository.saveAndFlush(new AppUser("alice@example.com", "$2a$10$hash", Role.USER));

		assertThat(appUserRepository.findByEmailIgnoreCase("ALICE@example.com"))
				.get()
				.extracting(AppUser::getRole)
				.isEqualTo(Role.USER);
		assertThat(appUserRepository.existsByEmailIgnoreCase("Alice@Example.com")).isTrue();
		assertThat(appUserRepository.existsByEmailIgnoreCase("bob@example.com")).isFalse();
	}

	@Test
	void databaseRejectsSameEmailWithDifferentCase() {
		appUserRepository.saveAndFlush(new AppUser("alice@example.com", "$2a$10$hash", Role.USER));

		assertThatExceptionOfType(DataIntegrityViolationException.class)
				.isThrownBy(() -> appUserRepository.saveAndFlush(new AppUser("Alice@Example.com", "$2a$10$other", Role.USER)));
	}

	@Test
	void roleIsStoredAsText() {
		Long id = appUserRepository.saveAndFlush(new AppUser("admin@example.com", "$2a$10$hash", Role.ADMIN)).getId();

		assertThat(jdbc.queryForObject("SELECT role FROM app_user WHERE id = ?", String.class, id)).isEqualTo("ADMIN");
	}

}
