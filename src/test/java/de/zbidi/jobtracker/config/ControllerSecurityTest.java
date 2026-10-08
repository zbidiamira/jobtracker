package de.zbidi.jobtracker.config;

import java.util.List;

import de.zbidi.jobtracker.auth.AuthService;
import de.zbidi.jobtracker.common.GlobalExceptionHandler;
import de.zbidi.jobtracker.common.PageResponse;
import de.zbidi.jobtracker.company.CompanyService;
import de.zbidi.jobtracker.jobapplication.JobApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * Two ways to fake an authenticated user in MockMvc:
 * <ul>
 *     <li>{@code @WithMockUser(roles = ...)}: a plain username/password login with roles. Good for checking the
 *     role rules (who may DELETE). It carries <b>no JWT</b>, so endpoints that need the caller's id can't work.</li>
 *     <li>{@code jwt()} post-processor: a validated JWT with any claims. Needed wherever the owner (sub claim) matters.</li>
 * </ul>
 */
@WebMvcTest
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class ControllerSecurityTest {

	@Autowired
	MockMvcTester mvc;

	@MockitoBean
	CompanyService companyService;

	@MockitoBean
	JobApplicationService jobApplicationService;

	@MockitoBean
	AuthService authService;

	// --- no token ---

	@Test
	void noTokenReturns401() {
		assertThat(mvc.get().uri("/api/companies").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.get().uri("/api/applications/5").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.delete().uri("/api/companies/1").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		verifyNoInteractions(companyService, jobApplicationService);
	}

	// --- @WithMockUser: role rules ---

	@Test
	@WithMockUser(roles = "USER")
	void userCanGetCompanies() {
		given(companyService.list(any())).willReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

		assertThat(mvc.get().uri("/api/companies").exchange()).hasStatus(HttpStatus.OK);
	}

	@Test
	@WithMockUser(roles = "USER")
	void userCannotDeleteCompany() {
		MvcTestResult result = mvc.delete().uri("/api/companies/1").exchange();

		assertThat(result).hasStatus(HttpStatus.FORBIDDEN).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		verifyNoInteractions(companyService);
	}

	@Test
	@WithMockUser(roles = "USER")
	void userCannotDeleteApplication() {
		assertThat(mvc.delete().uri("/api/applications/5").exchange()).hasStatus(HttpStatus.FORBIDDEN);
		verifyNoInteractions(jobApplicationService);
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void adminCanDeleteCompany() {
		assertThat(mvc.delete().uri("/api/companies/1").exchange()).hasStatus(HttpStatus.NO_CONTENT);
		verify(companyService).delete(1L);
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void adminCanDeleteApplication() {
		assertThat(mvc.delete().uri("/api/applications/5").exchange()).hasStatus(HttpStatus.NO_CONTENT);
		verify(jobApplicationService).delete(5L);
	}

	/**
	 * The limit of @WithMockUser: the role check passes, but there is no JWT and so no user id. Owner-scoped
	 * endpoints answer 401 instead of guessing an owner. Use {@code jwt()} for these (see below).
	 */
	@Test
	@WithMockUser(roles = "USER")
	void withMockUserHasNoJwtSoOwnerScopedEndpointsReturn401() {
		MvcTestResult result = mvc.get().uri("/api/applications/5").exchange();

		assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		verifyNoInteractions(jobApplicationService);
	}

	// --- jwt(): the owner comes from the sub claim ---

	@Test
	void userIdIsTakenFromTheSubClaim() {
		given(jobApplicationService.search(eq(123L), any(), any())).willReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

		assertThat(mvc.get().uri("/api/applications/5").with(userWithSubject("123")).exchange()).hasStatus(HttpStatus.OK);
		assertThat(mvc.get().uri("/api/applications").with(userWithSubject("123")).exchange()).hasStatus(HttpStatus.OK);

		verify(jobApplicationService).get(123L, 5L);
		verify(jobApplicationService).search(eq(123L), any(), any());
	}

	@Test
	void differentSubjectMeansDifferentOwner() {
		mvc.get().uri("/api/applications/5").with(userWithSubject("7")).exchange();
		mvc.get().uri("/api/applications/5").with(userWithSubject("8")).exchange();

		verify(jobApplicationService).get(7L, 5L);
		verify(jobApplicationService).get(8L, 5L);
	}

	@Test
	void jwtWithoutAdminRoleCannotDelete() {
		assertThat(mvc.delete().uri("/api/applications/5").with(userWithSubject("7")).exchange())
				.hasStatus(HttpStatus.FORBIDDEN);
		verifyNoInteractions(jobApplicationService);
	}

	@Test
	void jwtWithAdminRoleCanDelete() {
		JwtRequestPostProcessor admin = jwt().jwt(token -> token.subject("1"))
				.authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));

		assertThat(mvc.delete().uri("/api/applications/5").with(admin).exchange()).hasStatus(HttpStatus.NO_CONTENT);
		verify(jobApplicationService).delete(5L);
	}

	private static JwtRequestPostProcessor userWithSubject(String subject) {
		return jwt().jwt(token -> token.subject(subject)).authorities(new SimpleGrantedAuthority("ROLE_USER"));
	}

}
