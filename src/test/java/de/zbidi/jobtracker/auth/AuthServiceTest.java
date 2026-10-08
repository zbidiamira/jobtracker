package de.zbidi.jobtracker.auth;

import java.util.Optional;

import de.zbidi.jobtracker.user.AppUser;
import de.zbidi.jobtracker.user.AppUserRepository;
import de.zbidi.jobtracker.user.EmailAlreadyRegisteredException;
import de.zbidi.jobtracker.user.Role;
import de.zbidi.jobtracker.user.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Register = create the account (UserService, tested in UserServiceTest) + issue a token. Login = check password + token.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	private static final AccessToken TOKEN = new AccessToken("signed.jwt.value", 3600);

	@Mock
	UserService userService;

	@Mock
	AppUserRepository appUserRepository;

	@Mock
	PasswordEncoder passwordEncoder;

	@Mock
	TokenService tokenService;

	@InjectMocks
	AuthService authService;

	@Test
	void registerCreatesUserViaUserServiceAndReturnsToken() {
		given(userService.register("alice@example.com", "s3cret-pass")).willReturn(alice());
		given(tokenService.issue(7L, Role.USER)).willReturn(TOKEN);

		AuthResponse response = authService.register(new RegisterRequest("alice@example.com", "s3cret-pass"));

		assertThat(response).isEqualTo(new AuthResponse("signed.jwt.value", "Bearer", 3600));
	}

	@Test
	void registerDuplicateEmailIssuesNoToken() {
		given(userService.register(any(), any())).willThrow(new EmailAlreadyRegisteredException("alice@example.com"));

		assertThatExceptionOfType(EmailAlreadyRegisteredException.class)
				.isThrownBy(() -> authService.register(new RegisterRequest("alice@example.com", "s3cret-pass")));
		verify(tokenService, never()).issue(any(), any());
	}

	@Test
	void loginWithCorrectPasswordReturnsToken() {
		given(appUserRepository.findByEmailIgnoreCase("alice@example.com")).willReturn(Optional.of(alice()));
		given(passwordEncoder.matches("s3cret-pass", "$2a$10$hash")).willReturn(true);
		given(tokenService.issue(7L, Role.USER)).willReturn(TOKEN);

		assertThat(authService.login(new LoginRequest("Alice@example.com", "s3cret-pass")).accessToken())
				.isEqualTo("signed.jwt.value");
	}

	@Test
	void loginWithWrongPasswordThrowsBadCredentials() {
		given(appUserRepository.findByEmailIgnoreCase("alice@example.com")).willReturn(Optional.of(alice()));

		assertThatExceptionOfType(BadCredentialsException.class)
				.isThrownBy(() -> authService.login(new LoginRequest("alice@example.com", "wrong")))
				.withMessage("Invalid email or password");
		verify(tokenService, never()).issue(any(), any());
	}

	@Test
	void loginWithUnknownEmailThrowsTheSameBadCredentials() {
		given(appUserRepository.findByEmailIgnoreCase("nobody@example.com")).willReturn(Optional.empty());

		// same exception and message as a wrong password: callers can't find out which emails exist
		assertThatExceptionOfType(BadCredentialsException.class)
				.isThrownBy(() -> authService.login(new LoginRequest("nobody@example.com", "whatever")))
				.withMessage("Invalid email or password");
	}

	private static AppUser alice() {
		AppUser user = new AppUser("alice@example.com", "$2a$10$hash", Role.USER);
		ReflectionTestUtils.setField(user, "id", 7L);
		return user;
	}

}
