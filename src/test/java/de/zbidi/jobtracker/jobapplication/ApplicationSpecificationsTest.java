package de.zbidi.jobtracker.jobapplication;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import de.zbidi.jobtracker.MutableClock;
import de.zbidi.jobtracker.PostgresTestcontainersConfiguration;
import de.zbidi.jobtracker.TestClockConfiguration;
import de.zbidi.jobtracker.company.Company;
import de.zbidi.jobtracker.config.JpaAuditingConfig;
import de.zbidi.jobtracker.user.AppUser;
import de.zbidi.jobtracker.user.Role;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The search filters against real Postgres SQL. The test clock decides each application's created_at.
 */
@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresTestcontainersConfiguration.class, JpaAuditingConfig.class, TestClockConfiguration.class})
class ApplicationSpecificationsTest {

	private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

	@Autowired
	JobApplicationRepository repository;

	@Autowired
	TestEntityManager em;

	@Autowired
	MutableClock clock;

	@Autowired
	EntityManagerFactory entityManagerFactory;

	private AppUser owner;
	private Company acme;
	private Company globex;

	@BeforeEach
	void setUp() {
		owner = em.persist(new AppUser("alice@example.com", "$2a$10$hash", Role.USER));
		acme = em.persist(new Company("ACME GmbH", "Berlin", null));
		globex = em.persist(new Company("Globex Corp", "Munich", null));
	}

	@Test
	void noFiltersReturnsAll() {
		createAt("2026-10-01T10:00", acme, "A Dev");
		createAt("2026-10-02T10:00", globex, "B Dev");

		assertThat(positions(search(null, null, null, null, null))).containsExactly("A Dev", "B Dev");
	}

	@Test
	void filtersByStatus() {
		createAt("2026-10-01T10:00", acme, "Applied Dev", Status.APPLIED);
		createAt("2026-10-01T10:00", acme, "Saved Dev");

		assertThat(positions(search(Status.APPLIED, null, null, null, null))).containsExactly("Applied Dev");
	}

	@Test
	void filtersByCompanyNameContainsIgnoringCase() {
		createAt("2026-10-01T10:00", acme, "At Acme");
		createAt("2026-10-01T10:00", globex, "At Globex");

		assertThat(positions(search(null, "acme", null, null, null))).containsExactly("At Acme");
	}

	@Test
	void filtersByPositionContainsIgnoringCase() {
		createAt("2026-10-01T10:00", acme, "Senior Java Developer");
		createAt("2026-10-01T10:00", acme, "Kotlin Developer");

		assertThat(positions(search(null, null, "JAVA", null, null))).containsExactly("Senior Java Developer");
	}

	@Test
	void createdFromIsInclusiveInBerlinTime() {
		createAt("2026-09-30T23:30", acme, "Day before");
		// 00:30 in Berlin is still 2026-09-30 in UTC, but counts as 2026-10-01
		createAt("2026-10-01T00:30", acme, "Just after midnight");

		assertThat(positions(search(null, null, null, LocalDate.of(2026, 10, 1), null)))
				.containsExactly("Just after midnight");
	}

	@Test
	void createdToIsInclusiveWholeDay() {
		createAt("2026-10-07T23:59", acme, "Last minute");
		createAt("2026-10-08T00:00", acme, "Next day");

		assertThat(positions(search(null, null, null, null, LocalDate.of(2026, 10, 7))))
				.containsExactly("Last minute");
	}

	@Test
	void combinesAllFiltersWithAnd() {
		createAt("2026-10-03T10:00", acme, "Java Backend", Status.APPLIED);   // matches everything
		createAt("2026-10-03T10:00", acme, "Java Frontend");                   // wrong status
		createAt("2026-10-03T10:00", globex, "Java Platform", Status.APPLIED); // wrong company
		createAt("2026-10-03T10:00", acme, "Kotlin Backend", Status.APPLIED);  // wrong position
		createAt("2026-09-20T10:00", acme, "Java Legacy", Status.APPLIED);     // too early

		assertThat(positions(search(Status.APPLIED, "acme", "java", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7))))
				.containsExactly("Java Backend");
	}

	@Test
	void likeWildcardsInInputAreTreatedLiterally() {
		createAt("2026-10-01T10:00", acme, "100% Remote Dev");
		createAt("2026-10-01T10:00", acme, "Backend Dev");

		assertThat(positions(search(null, null, "100%", null, null))).containsExactly("100% Remote Dev");
		assertThat(positions(search(null, null, "%", null, null))).containsExactly("100% Remote Dev");
		assertThat(positions(search(null, null, "_", null, null))).isEmpty();
	}

	@Test
	void blankTextFiltersAreIgnored() {
		createAt("2026-10-01T10:00", acme, "A Dev");

		assertThat(positions(search(null, "  ", "", null, null))).containsExactly("A Dev");
	}

	// --- ownership: every search is limited to the caller's own applications ---

	@Test
	void onlyReturnsTheOwnersApplications() {
		createAt("2026-10-01T10:00", acme, "Alice Dev");
		saveForOtherOwner(acme, "Bob Dev");

		assertThat(positions(search(null, null, null, null, null))).containsExactly("Alice Dev");
	}

	@Test
	void filtersOnlyApplyWithinTheOwnersApplications() {
		createAt("2026-10-01T10:00", acme, "Java Developer", Status.APPLIED);
		saveForOtherOwner(acme, "Java Developer");

		assertThat(positions(search(null, "acme", "java", null, null))).containsExactly("Java Developer");
		assertThat(repository.findAll(ApplicationSpecifications.matching(owner.getId(),
				search(null, "acme", "java", null, null), BERLIN), PageRequest.of(0, 10)).getTotalElements()).isEqualTo(1);
	}

	private void saveForOtherOwner(Company company, String position) {
		AppUser bob = em.persist(new AppUser("bob@example.com", "$2a$10$hash", Role.USER));
		repository.saveAndFlush(new JobApplication(bob, company, position, null));
	}

	// --- N+1 ---

	@Test
	void loadsCompaniesInTheSameQueryAsTheApplications() {
		for (int i = 1; i <= 5; i++) {
			Company company = em.persist(new Company("Company " + i, null, null));
			createAt("2026-10-01T10:00", company, "Dev " + i);
		}
		em.clear(); // companies must come from the database, not from the persistence context
		Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		stats.clear();

		// page size 3 of 5 results: 1 select for the rows + 1 count query
		var page = repository.findAll(ApplicationSpecifications.matching(owner.getId(), search(null, null, "dev", null, null), BERLIN),
				PageRequest.of(0, 3, Sort.by("position")));
		List<String> companyNames = page.map(application -> application.getCompany().getName()).getContent();

		assertThat(companyNames).containsExactly("Company 1", "Company 2", "Company 3");
		// without the fetch join this would be 2 + 3 (one extra SELECT per company): the N+1 problem
		assertThat(stats.getPrepareStatementCount()).isEqualTo(2);
	}

	private static JobApplicationSearch search(Status status, String companyName, String position,
			LocalDate createdFrom, LocalDate createdTo) {
		return new JobApplicationSearch(status, companyName, position, createdFrom, createdTo);
	}

	/** Creates an application "at" the given Berlin local time and walks it through the given statuses. */
	private void createAt(String berlinDateTime, Company company, String position, Status... path) {
		clock.setInstant(LocalDateTime.parse(berlinDateTime).atZone(BERLIN).toInstant());
		JobApplication application = new JobApplication(owner, company, position, null);
		for (Status status : path) {
			application.changeStatus(status);
		}
		repository.saveAndFlush(application);
	}

	private List<String> positions(JobApplicationSearch search) {
		return repository.findAll(ApplicationSpecifications.matching(owner.getId(), search, BERLIN),
						PageRequest.of(0, 50, Sort.by("position")))
				.map(JobApplication::getPosition)
				.getContent();
	}

}
