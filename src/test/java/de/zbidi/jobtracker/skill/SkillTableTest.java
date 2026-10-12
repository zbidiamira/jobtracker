package de.zbidi.jobtracker.skill;

import java.util.UUID;

import de.zbidi.jobtracker.PostgresTestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V11: skill belongs to one user; a name exists once per user (case-insensitive).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestcontainersConfiguration.class)
class SkillTableTest {

	@Autowired
	JdbcTemplate jdbc;

	long alice;
	long bob;

	@BeforeEach
	void setUp() {
		alice = user();
		bob = user();
	}

	@Test
	void skillTableHasTheExpectedColumns() {
		assertThat(jdbc.queryForList("""
				SELECT column_name FROM information_schema.columns
				WHERE table_schema = 'public' AND table_name = 'skill'
				ORDER BY ordinal_position
				""", String.class))
				.containsExactly("id", "user_id", "name", "years", "level");
	}

	@Test
	void nameIsUniquePerUserIgnoringCase() {
		insert(alice, "Java", 5);

		assertThatThrownBy(() -> insert(alice, "JAVA", 2)).isInstanceOf(DuplicateKeyException.class);
	}

	@Test
	void twoUsersMayHaveTheSameSkill() {
		insert(alice, "Java", 5);
		insert(bob, "Java", 1);

		assertThat(jdbc.queryForObject("SELECT count(*) FROM skill WHERE name = 'Java'", Integer.class)).isEqualTo(2);
	}

	@Test
	void yearsMustNotBeNegative() {
		assertThatThrownBy(() -> insert(alice, "Kotlin", -1)).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void skillNeedsAnExistingUser() {
		assertThatThrownBy(() -> insert(999_999L, "Go", 1)).isInstanceOf(DataIntegrityViolationException.class);
	}

	private long user() {
		return jdbc.queryForObject("INSERT INTO app_user (email, password_hash, role) VALUES (?, 'x', 'USER') RETURNING id",
				Long.class, "skill-" + UUID.randomUUID() + "@example.com");
	}

	private void insert(long userId, String name, int years) {
		jdbc.update("INSERT INTO skill (user_id, name, years, level) VALUES (?, ?, ?, 'ADVANCED')", userId, name, years);
	}

}
