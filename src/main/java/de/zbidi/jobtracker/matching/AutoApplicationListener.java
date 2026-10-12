package de.zbidi.jobtracker.matching;

import de.zbidi.jobtracker.company.Company;
import de.zbidi.jobtracker.company.CompanyRepository;
import de.zbidi.jobtracker.jobapplication.DuplicateApplicationException;
import de.zbidi.jobtracker.jobapplication.JobApplicationResponse;
import de.zbidi.jobtracker.jobapplication.JobApplicationService;
import de.zbidi.jobtracker.jobapplication.NewJobApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Turns a good match into a job application in status SAVED for that user (company reused by name, or created).
 * Idempotent: Kafka delivers at least once, and the second delivery hits the duplicate rule (same link or same
 * company + position for the owner) and is ignored. Switch off with
 * {@code jobtracker.matching.auto-create-application=false}.
 */
@Component
@ConditionalOnProperty(name = "jobtracker.matching.auto-create-application", havingValue = "true",
		matchIfMissing = true)
public class AutoApplicationListener {

	private static final Logger log = LoggerFactory.getLogger(AutoApplicationListener.class);

	private final CompanyRepository companyRepository;
	private final JobApplicationService jobApplicationService;

	public AutoApplicationListener(CompanyRepository companyRepository, JobApplicationService jobApplicationService) {
		this.companyRepository = companyRepository;
		this.jobApplicationService = jobApplicationService;
	}

	/** Own consumer group (next to the status history's), and its own JSON target type. */
	@KafkaListener(topics = MatchEvents.TOPIC, groupId = "${spring.kafka.consumer.group-id}-auto-applications",
			properties = "spring.json.value.default.type=de.zbidi.jobtracker.matching.GoodMatchFound")
	public void onGoodMatch(GoodMatchFound match) {
		Company company = companyRepository.findByNameIgnoreCase(match.companyName())
				.orElseGet(() -> companyRepository.save(new Company(match.companyName(), null, null)));
		try {
			JobApplicationResponse created = jobApplicationService.create(match.userId(),
					new NewJobApplication(company.getId(), null, match.title(), match.url()));
			log.info("Good match {} (score {}): application {} saved for user {}", match.postingId(),
					match.matchScore(), created == null ? "?" : created.id(), match.userId());
		}
		catch (DuplicateApplicationException ex) {
			log.debug("Good match {} for user {} already has an application", match.postingId(), match.userId());
		}
	}

}
