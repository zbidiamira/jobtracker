package de.zbidi.jobtracker.auth;

import java.util.List;

import com.jayway.jsonpath.JsonPath;
import de.zbidi.jobtracker.common.GlobalExceptionHandler;
import de.zbidi.jobtracker.config.SecurityConfig;
import de.zbidi.jobtracker.user.EmailAlreadyRegisteredException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * The auth endpoints are public: no token is sent in any of these requests.
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AuthControllerTest {

	private static final AuthResponse TOKEN = new AuthResponse("signed.jwt.value", "Bearer", 3600);

	@Autowired
	MockMvcTester mvc;

	@MockitoBean
	AuthService authService;

	@Test
	void registerReturns201WithToken() throws Exception {
		given(authService.register(new RegisterRequest("alice@example.com", "s3cret-pass"))).willReturn(TOKEN);

		MvcTestResult result = post("/api/auth/register", """
				{"email": "alice@example.com", "password": "s3cret-pass"}
				""");

		assertThat(result).hasStatus(HttpStatus.CREATED);
		assertThat(result).bodyJson().extractingPath("$.accessToken").isEqualTo("signed.jwt.value");
		assertThat(result).bodyJson().extractingPath("$.tokenType").isEqualTo("Bearer");
		assertThat(result).bodyJson().extractingPath("$.expiresIn").isEqualTo(3600);
	}

	@Test
	void registerInvalidReturns400ProblemWithFieldErrors() throws Exception {
		MvcTestResult result = post("/api/auth/register", """
				{"email": "not-an-email", "password": "short"}
				""");

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		List<String> fields = JsonPath.read(result.getResponse().getContentAsString(), "$.errors[*].field");
		assertThat(fields).containsExactlyInAnyOrder("email", "password");
		verifyNoInteractions(authService);
	}

	@Test
	void registerDuplicateEmailReturns409() {
		given(authService.register(any())).willThrow(new EmailAlreadyRegisteredException("alice@example.com"));

		MvcTestResult result = post("/api/auth/register", """
				{"email": "alice@example.com", "password": "s3cret-pass"}
				""");

		assertThat(result).hasStatus(HttpStatus.CONFLICT).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(409);
		assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Email already registered");
		assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("The email alice@example.com is already registered");
		assertThat(result).bodyJson().extractingPath("$.instance").isEqualTo("/api/auth/register");
	}

	@Test
	void loginReturns200WithToken() {
		given(authService.login(new LoginRequest("alice@example.com", "s3cret-pass"))).willReturn(TOKEN);

		MvcTestResult result = post("/api/auth/login", """
				{"email": "alice@example.com", "password": "s3cret-pass"}
				""");

		assertThat(result).hasStatus(HttpStatus.OK);
		assertThat(result).bodyJson().extractingPath("$.accessToken").isEqualTo("signed.jwt.value");
	}

	@Test
	void loginBadCredentialsReturns401Problem() {
		given(authService.login(any())).willThrow(new BadCredentialsException("Invalid email or password"));

		MvcTestResult result = post("/api/auth/login", """
				{"email": "alice@example.com", "password": "wrong"}
				""");

		assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("Invalid email or password");
	}

	private MvcTestResult post(String uri, String json) {
		return mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
	}

}
