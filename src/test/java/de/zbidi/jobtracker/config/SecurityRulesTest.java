package de.zbidi.jobtracker.config;

import java.util.List;

import de.zbidi.jobtracker.TestJwt;
import de.zbidi.jobtracker.analysis.PostingAnalyzer;
import de.zbidi.jobtracker.matching.MatchQueryService;
import de.zbidi.jobtracker.jobsource.JobFetchService;
import de.zbidi.jobtracker.matching.MatchingService;
import de.zbidi.jobtracker.auth.AuthResponse;
import de.zbidi.jobtracker.auth.AuthService;
import de.zbidi.jobtracker.common.GlobalExceptionHandler;
import de.zbidi.jobtracker.common.PageResponse;
import de.zbidi.jobtracker.company.CompanyResponse;
import de.zbidi.jobtracker.company.CompanyService;
import de.zbidi.jobtracker.jobapplication.JobApplicationService;
import de.zbidi.jobtracker.posting.ImportService;
import de.zbidi.jobtracker.skill.SkillService;
import de.zbidi.jobtracker.posting.ImportStatusResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * The SecurityFilterChain rules, over all controllers (services mocked). Uses the real JwtDecoder for
 * invalid tokens; valid tokens come from {@link TestJwt}.
 */
@WebMvcTest
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class SecurityRulesTest {

	private static final MockMultipartFile CSV = new MockMultipartFile("file", "postings.csv", "text/csv",
			"title,company,city,url\nDev,ACME,Berlin,https://acme.example/1\n".getBytes());

	@Autowired
	MockMvcTester mvc;

	@MockitoBean
	CompanyService companyService;

	@MockitoBean
	JobApplicationService jobApplicationService;

	@MockitoBean
	AuthService authService;

	@MockitoBean
	ImportService importService;

	@MockitoBean
	SkillService skillService;

	@MockitoBean
	PostingAnalyzer postingAnalyzer;

	@MockitoBean
	MatchQueryService matchQueryService;

	@MockitoBean
	MatchingService matchingService;

	@MockitoBean
	JobFetchService jobFetchService;

	// --- 401 ---

	@Test
	void apiWithoutTokenReturns401ProblemWithWwwAuthenticateBearer() {
		MvcTestResult result = mvc.get().uri("/api/companies").exchange();

		assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)).startsWith("Bearer");
		assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Unauthorized");
		verifyNoInteractions(companyService);
	}

	@Test
	void applicationsWithoutTokenReturn401() {
		assertThat(mvc.get().uri("/api/applications/1").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.get().uri("/api/applications/search").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		verifyNoInteractions(jobApplicationService);
	}

	@Test
	void invalidTokenReturns401() {
		MvcTestResult result = mvc.get().uri("/api/companies").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt").exchange();

		assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)).contains("invalid_token");
	}

	@Test
	void tokenWithNonNumericSubjectReturns401NotServerError() {
		MvcTestResult result = mvc.get().uri("/api/applications/1")
				.with(jwt().jwt(token -> token.subject("alice@example.com")).authorities(new SimpleGrantedAuthority("ROLE_USER")))
				.exchange();

		assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		verifyNoInteractions(jobApplicationService);
	}

	// --- public ---

	@Test
	void authEndpointsNeedNoToken() {
		given(authService.login(any())).willReturn(new AuthResponse("t", "Bearer", 3600));

		MvcTestResult result = mvc.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email": "alice@example.com", "password": "s3cret-pass"}
						""").exchange();

		assertThat(result).hasStatus(HttpStatus.OK);
	}

	// --- roles ---

	@Test
	void userMayReadAndCreate() {
		given(companyService.list(any())).willReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

		assertThat(mvc.get().uri("/api/companies").with(TestJwt.user(7L)).exchange()).hasStatus(HttpStatus.OK);
	}

	@Test
	void deleteApplicationAsUserReturns403Problem() {
		MvcTestResult result = mvc.delete().uri("/api/applications/5").with(TestJwt.user(7L)).exchange();

		assertThat(result).hasStatus(HttpStatus.FORBIDDEN).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Forbidden");
		verifyNoInteractions(jobApplicationService);
	}

	@Test
	void deleteApplicationAsAdminReturns204() {
		assertThat(mvc.delete().uri("/api/applications/5").with(TestJwt.admin(1L)).exchange())
				.hasStatus(HttpStatus.NO_CONTENT);
		verify(jobApplicationService).delete(5L);
	}

	@Test
	void deleteCompanyAsUserReturns403() {
		assertThat(mvc.delete().uri("/api/companies/1").with(TestJwt.user(7L)).exchange()).hasStatus(HttpStatus.FORBIDDEN);
		verifyNoInteractions(companyService);
	}

	@Test
	void deleteCompanyAsAdminReturns204() {
		assertThat(mvc.delete().uri("/api/companies/1").with(TestJwt.admin(1L)).exchange())
				.hasStatus(HttpStatus.NO_CONTENT);
	}

	@Test
	void adminApiAsUserReturns403Problem() {
		MvcTestResult result = mvc.get().uri("/api/admin/imports/1").with(TestJwt.user(7L)).exchange();

		assertThat(result).hasStatus(HttpStatus.FORBIDDEN).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		verifyNoInteractions(importService);
	}

	@Test
	void adminApiWithoutTokenReturns401() {
		assertThat(mvc.get().uri("/api/admin/imports/1").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		verifyNoInteractions(importService);
	}

	@Test
	void importUploadAsUserReturns403Problem() {
		MvcTestResult result = mvc.post().uri("/api/admin/imports").multipart().file(CSV)
				.with(TestJwt.user(7L)).exchange();

		assertThat(result).hasStatus(HttpStatus.FORBIDDEN).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Forbidden");
		verifyNoInteractions(importService);
	}

	@Test
	void importUploadAsAdminReturns202() {
		given(importService.start(any())).willReturn(
				new ImportStatusResponse(9L, "STARTING", "UNKNOWN", null, null, 0, 0, 0, 0, List.of()));

		MvcTestResult result = mvc.post().uri("/api/admin/imports").multipart().file(CSV)
				.with(TestJwt.admin(1L)).exchange();

		assertThat(result).hasStatus(HttpStatus.ACCEPTED);
		assertThat(result).bodyJson().extractingPath("$.executionId").isEqualTo(9);
	}

	@Test
	void adminApiAsAdminIsAllowed() {
		given(importService.status(1L)).willReturn(
				new ImportStatusResponse(1L, "COMPLETED", "COMPLETED", null, null, 0, 0, 0, 0, List.of()));

		assertThat(mvc.get().uri("/api/admin/imports/1").with(TestJwt.admin(1L)).exchange()).hasStatus(HttpStatus.OK);
	}

	// --- stateless API ---

	@Test
	void noCsrfTokenNeededForPost() {
		given(companyService.create(any())).willReturn(new CompanyResponse(1L, "ACME GmbH", null, null));

		MvcTestResult result = mvc.post().uri("/api/companies").with(TestJwt.user(7L))
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"name": "ACME GmbH"}
						""").exchange();

		assertThat(result).hasStatus(HttpStatus.CREATED);
	}

	@Test
	void noSessionCookieIsCreated() {
		given(companyService.list(any())).willReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

		MvcTestResult result = mvc.get().uri("/api/companies").with(TestJwt.user(7L)).exchange();

		assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
		assertThat(result.getRequest().getSession(false)).isNull();
	}

}
