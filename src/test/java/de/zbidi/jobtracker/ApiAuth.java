package de.zbidi.jobtracker;

import java.io.UnsupportedEncodingException;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end tests get real tokens the way a client does: through POST /api/auth/register.
 */
public final class ApiAuth {

	public static final String PASSWORD = "s3cret-pass";

	private ApiAuth() {
	}

	/** Registers a new, unique user and returns the {@code Authorization} header value. */
	public static String registerNewUser(MockMvcTester mvc, String name) {
		return register(mvc, name + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com");
	}

	public static String register(MockMvcTester mvc, String email) {
		MvcTestResult result = mvc.post().uri("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, PASSWORD)).exchange();
		assertThat(result).hasStatus(HttpStatus.CREATED);
		return "Bearer " + JsonPath.read(content(result), "$.accessToken");
	}

	public static String login(MockMvcTester mvc, String email) {
		MvcTestResult result = mvc.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, PASSWORD)).exchange();
		assertThat(result).hasStatus(HttpStatus.OK);
		return "Bearer " + JsonPath.read(content(result), "$.accessToken");
	}

	public static String content(MvcTestResult result) {
		try {
			return result.getResponse().getContentAsString();
		}
		catch (UnsupportedEncodingException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
