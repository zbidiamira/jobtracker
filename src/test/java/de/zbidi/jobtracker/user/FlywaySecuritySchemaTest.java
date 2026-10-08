package de.zbidi.jobtracker.user;

import de.zbidi.jobtracker.PostgresTestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V5 creates app_user, V6 gives every job application a mandatory owner.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestcontainersConfiguration.class)
class FlywaySecuritySchemaTest {

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void appUserTableHasTheExpectedColumns() {
		assertThat(jdbc.queryForList("""
				SELECT column_name FROM information_schema.columns
				WHERE table_schema = 'public' AND table_name = 'app_user'
				ORDER BY ordinal_position
				""", String.class))
				.containsExactly("id", "email", "password_hash", "role");
	}

	@Test
	void jobApplicationOwnerIsMandatoryForeignKeyToAppUser() {
		assertThat(jdbc.queryForObject("""
				SELECT is_nullable FROM information_schema.columns
				WHERE table_schema = 'public' AND table_name = 'job_application' AND column_name = 'owner_id'
				""", String.class)).isEqualTo("NO");
		assertThat(jdbc.queryForObject("""
				SELECT ccu.table_name
				FROM information_schema.table_constraints tc
				JOIN information_schema.key_column_usage kcu ON tc.constraint_name = kcu.constraint_name
				JOIN information_schema.constraint_column_usage ccu ON tc.constraint_name = ccu.constraint_name
				WHERE tc.constraint_type = 'FOREIGN KEY' AND tc.table_name = 'job_application' AND kcu.column_name = 'owner_id'
				""", String.class)).isEqualTo("app_user");
	}

}
