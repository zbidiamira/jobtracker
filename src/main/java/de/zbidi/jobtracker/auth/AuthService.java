package de.zbidi.jobtracker.auth;

import java.util.Optional;
import java.util.UUID;

import de.zbidi.jobtracker.user.AppUser;
import de.zbidi.jobtracker.user.AppUserRepository;
import de.zbidi.jobtracker.user.UserService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Register and log in; both return an access token. Account creation itself is {@link UserService}'s job.
 */
@Service
@Transactional
public class AuthService {

	private static final String BAD_CREDENTIALS = "Invalid email or password";

	private final UserService userService;
	private final AppUserRepository appUserRepository;
	private final PasswordEncoder passwordEncoder;
	private final TokenService tokenService;

	/** Compared against when the email is unknown, so both failure cases take about the same time. */
	private volatile String dummyHash;

	public AuthService(UserService userService, AppUserRepository appUserRepository, PasswordEncoder passwordEncoder,
			TokenService tokenService) {
		this.userService = userService;
		this.appUserRepository = appUserRepository;
		this.passwordEncoder = passwordEncoder;
		this.tokenService = tokenService;
	}

	public AuthResponse register(RegisterRequest request) {
		AppUser user = userService.register(request.email(), request.password());
		return AuthResponse.bearer(tokenService.issue(user.getId(), user.getRole()));
	}

	/**
	 * @throws BadCredentialsException with the same message whether the email is unknown or the password is wrong
	 */
	@Transactional(readOnly = true)
	public AuthResponse login(LoginRequest request) {
		Optional<AppUser> user = appUserRepository.findByEmailIgnoreCase(UserService.normalize(request.email()));
		if (user.isEmpty()) {
			passwordEncoder.matches(request.password(), dummyHash());
			throw new BadCredentialsException(BAD_CREDENTIALS);
		}
		if (!passwordEncoder.matches(request.password(), user.get().getPasswordHash())) {
			throw new BadCredentialsException(BAD_CREDENTIALS);
		}
		return AuthResponse.bearer(tokenService.issue(user.get().getId(), user.get().getRole()));
	}

	private String dummyHash() {
		if (dummyHash == null) {
			dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
		}
		return dummyHash;
	}

}
