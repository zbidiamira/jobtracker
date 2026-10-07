package de.zbidi.jobtracker.company;

import java.util.List;

import de.zbidi.jobtracker.common.GlobalExceptionHandler;
import de.zbidi.jobtracker.common.PageResponse;
import de.zbidi.jobtracker.common.ResourceNotFoundException;
import de.zbidi.jobtracker.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@WebMvcTest(CompanyController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class CompanyControllerTest {

	private static final CompanyResponse ACME = new CompanyResponse(1L, "ACME GmbH", "Berlin", "https://acme.example");

	@Autowired
	MockMvcTester mvc;

	@MockitoBean
	CompanyService companyService;

	@Test
	void postCreatesCompanyReturns201WithLocation() {
		given(companyService.create(new CreateCompanyRequest("ACME GmbH", "Berlin", "https://acme.example")))
				.willReturn(ACME);

		MvcTestResult result = post("""
				{"name": "ACME GmbH", "city": "Berlin", "website": "https://acme.example"}
				""");

		assertThat(result).hasStatus(HttpStatus.CREATED);
		assertThat(result.getResponse().getHeader("Location")).endsWith("/api/companies/1");
		assertThat(result).bodyJson().extractingPath("$.id").isEqualTo(1);
		assertThat(result).bodyJson().extractingPath("$.name").isEqualTo("ACME GmbH");
	}

	@Test
	void postWithBlankNameReturns400ProblemWithFieldErrors() {
		MvcTestResult result = post("""
				{"name": "  "}
				""");

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("name");
		assertThat(result).bodyJson().extractingPath("$.errors[0].message").isNotNull();
		verifyNoInteractions(companyService);
	}

	@Test
	void postWithInvalidWebsiteReturns400() {
		MvcTestResult result = post("""
				{"name": "ACME GmbH", "website": "not a url"}
				""");

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("website");
	}

	@Test
	void postWithMalformedJsonReturns400() {
		MvcTestResult result = post("{\"name\": ");

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
	}

	@Test
	void getByIdReturns200() {
		given(companyService.get(1L)).willReturn(ACME);

		MvcTestResult result = mvc.get().uri("/api/companies/1").exchange();

		assertThat(result).hasStatus(HttpStatus.OK);
		assertThat(result).bodyJson().extractingPath("$.city").isEqualTo("Berlin");
	}

	@Test
	void getUnknownIdReturns404Problem() {
		given(companyService.get(99L)).willThrow(new ResourceNotFoundException("Company", 99L));

		MvcTestResult result = mvc.get().uri("/api/companies/99").exchange();

		assertThat(result).hasStatus(HttpStatus.NOT_FOUND).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("Company 99 not found");
	}

	@Test
	void listPassesPageableAndReturnsPageResponse() {
		given(companyService.list(any())).willReturn(new PageResponse<>(List.of(ACME), 1, 5, 6, 2));

		MvcTestResult result = mvc.get().uri("/api/companies?page=1&size=5&sort=name").exchange();

		assertThat(result).hasStatus(HttpStatus.OK);
		assertThat(result).bodyJson().extractingPath("$.content[0].name").isEqualTo("ACME GmbH");
		assertThat(result).bodyJson().extractingPath("$.totalElements").isEqualTo(6);

		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(companyService).list(pageable.capture());
		assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
		assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
		assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by("name"));
	}

	private MvcTestResult post(String json) {
		return mvc.post().uri("/api/companies").contentType(MediaType.APPLICATION_JSON).content(json).exchange();
	}

}
