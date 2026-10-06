package de.zbidi.jobtracker.recruiter;

import de.zbidi.jobtracker.PostgresTestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestcontainersConfiguration.class)
class RecruiterRepositoryTest {

	@Autowired
	RecruiterRepository recruiterRepository;

	@Test
	void findsRecruiterByEmailIgnoringCase() {
		// agency recruiter: no company
		recruiterRepository.saveAndFlush(new Recruiter("Anna Schmidt", "anna@hunters.example", null));

		assertThat(recruiterRepository.findByEmailIgnoreCase("ANNA@hunters.example"))
				.get()
				.extracting(Recruiter::getName)
				.isEqualTo("Anna Schmidt");
	}

	@Test
	void emailIsUniqueIgnoringCase() {
		recruiterRepository.saveAndFlush(new Recruiter("Anna Schmidt", "anna@hunters.example", null));

		assertThatExceptionOfType(DataIntegrityViolationException.class)
				.isThrownBy(() -> recruiterRepository.saveAndFlush(
						new Recruiter("Anna S.", "Anna@Hunters.example", null)));
	}

}
