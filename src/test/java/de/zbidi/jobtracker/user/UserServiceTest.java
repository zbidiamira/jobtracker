package de.zbidi.jobtracker.user;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

	@Mock
	AppUserRepository appUserRepository;

	@Mock
	PasswordEncoder passwordEncoder;

	@InjectMocks
	UserService userService;

	@Test
	void registerStoresBCryptHashNeverThePlainPassword() {
		given(passwordEncoder.encode("s3cret-pass")).willReturn("$2a$10$hashed");
		given(appUserRepository.save(any(AppUser.class))).willAnswer(invocation -> invocation.getArgument(0));

		userService.register("alice@example.com", "s3cret-pass");

		AppUser saved = captureSaved();
		assertThat(saved.getPasswordHash()).isEqualTo("$2a$10$hashed").doesNotContain("s3cret-pass");
	}

	@Test
	void registerNormalizesEmailAndAssignsRoleUser() {
		given(passwordEncoder.encode("s3cret-pass")).willReturn("$2a$10$hashed");
		given(appUserRepository.save(any(AppUser.class))).willAnswer(invocation -> invocation.getArgument(0));

		AppUser user = userService.register("  Alice@Example.COM ", "s3cret-pass");

		assertThat(user.getEmail()).isEqualTo("alice@example.com");
		assertThat(user.getRole()).isEqualTo(Role.USER);
		assertThat(captureSaved()).isSameAs(user);
	}

	@Test
	void registerDuplicateEmailThrowsEmailAlreadyRegisteredAndSavesNothing() {
		given(appUserRepository.existsByEmailIgnoreCase("alice@example.com")).willReturn(true);

		assertThatExceptionOfType(EmailAlreadyRegisteredException.class)
				.isThrownBy(() -> userService.register("ALICE@example.com", "s3cret-pass"))
				.withMessageContaining("alice@example.com");
		verify(appUserRepository, never()).save(any());
		verify(passwordEncoder, never()).encode(any());
	}

	private AppUser captureSaved() {
		ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
		verify(appUserRepository).save(saved.capture());
		return saved.getValue();
	}

}
