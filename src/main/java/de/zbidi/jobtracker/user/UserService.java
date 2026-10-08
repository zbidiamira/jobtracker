package de.zbidi.jobtracker.user;

import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User accounts. Passwords are only ever stored as BCrypt hashes (salted, deliberately slow).
 */
@Service
@Transactional
public class UserService {

	private final AppUserRepository appUserRepository;
	private final PasswordEncoder passwordEncoder;

	public UserService(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder) {
		this.appUserRepository = appUserRepository;
		this.passwordEncoder = passwordEncoder;
	}

	/**
	 * Creates an account with role USER. The email is trimmed and lower-cased; duplicates are checked ignoring case
	 * (the unique index on lower(email) catches concurrent registrations).
	 *
	 * @throws EmailAlreadyRegisteredException if the email already has an account (409)
	 */
	public AppUser register(String email, String rawPassword) {
		String normalizedEmail = normalize(email);
		if (appUserRepository.existsByEmailIgnoreCase(normalizedEmail)) {
			throw new EmailAlreadyRegisteredException(normalizedEmail);
		}
		return appUserRepository.save(new AppUser(normalizedEmail, passwordEncoder.encode(rawPassword), Role.USER));
	}

	public static String normalize(String email) {
		return email.strip().toLowerCase(Locale.ROOT);
	}

}
