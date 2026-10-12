package de.zbidi.jobtracker.skill;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import de.zbidi.jobtracker.user.AppUser;
import de.zbidi.jobtracker.user.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dev profile only: gives the configured user ({@code jobtracker.seed.skills.owner-email}) the skills from
 * {@code seed/skills.csv} at startup, so the AI posting analyzer has real data to work with.
 * <p>
 * Idempotent: a skill the user already has (any spelling) is left as it is, so changes made in the app survive
 * restarts. The user must exist: register first, then restart.
 */
@Component
@Profile("dev")
public class SkillSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(SkillSeeder.class);

	/** One line of the seed file. */
	public record SeedSkill(String name, int years, SkillLevel level) {
	}

	private final AppUserRepository userRepository;
	private final SkillRepository skillRepository;
	private final String ownerEmail;
	private final Resource seedFile;

	public SkillSeeder(AppUserRepository userRepository, SkillRepository skillRepository,
			@Value("${jobtracker.seed.skills.owner-email}") String ownerEmail,
			@Value("classpath:seed/skills.csv") Resource seedFile) {
		this.userRepository = userRepository;
		this.skillRepository = skillRepository;
		this.ownerEmail = ownerEmail;
		this.seedFile = seedFile;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		Optional<AppUser> owner = userRepository.findByEmailIgnoreCase(ownerEmail);
		if (owner.isEmpty()) {
			log.warn("Skill seed skipped: no user {}; register this account first, then restart.", ownerEmail);
			return;
		}
		Long ownerId = owner.get().getId();
		int added = 0;
		int present = 0;
		for (SeedSkill skill : parse(seedFile)) {
			if (skillRepository.existsByOwnerIdAndNameIgnoreCase(ownerId, skill.name())) {
				present++;
			}
			else {
				skillRepository.save(new Skill(ownerId, skill.name(), skill.years(), skill.level()));
				added++;
			}
		}
		log.info("Skill seed for {}: {} skill(s) added, {} already present", ownerEmail, added, present);
	}

	/**
	 * {@code name,years,level} per line; a header line, blank lines and {@code #} comments are skipped.
	 *
	 * @throws IllegalStateException naming the line, if a line can't be read
	 */
	public static List<SeedSkill> parse(Resource csv) {
		List<String> lines;
		try {
			lines = csv.getContentAsString(StandardCharsets.UTF_8).lines().toList();
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Can't read " + csv.getDescription(), ex);
		}
		List<SeedSkill> skills = new ArrayList<>();
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i).strip();
			if (line.isEmpty() || line.startsWith("#") || line.equalsIgnoreCase("name,years,level")) {
				continue;
			}
			String[] fields = line.split(",");
			try {
				if (fields.length != 3) {
					throw new IllegalArgumentException("expected name,years,level");
				}
				skills.add(new SeedSkill(fields[0].strip(), Integer.parseInt(fields[1].strip()),
						SkillLevel.valueOf(fields[2].strip().toUpperCase(Locale.ROOT))));
			}
			catch (IllegalArgumentException ex) {
				throw new IllegalStateException("Skill seed, line %d: '%s' (%s)".formatted(i + 1, line,
						ex.getMessage()), ex);
			}
		}
		return skills;
	}

}
