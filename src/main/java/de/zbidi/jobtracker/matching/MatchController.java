package de.zbidi.jobtracker.matching;

import de.zbidi.jobtracker.auth.CurrentUser;
import de.zbidi.jobtracker.common.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The AI's verdicts on postings for the caller, best match first. */
@RestController
public class MatchController {

	private final MatchQueryService matchQueryService;

	public MatchController(MatchQueryService matchQueryService) {
		this.matchQueryService = matchQueryService;
	}

	@GetMapping("/api/matches")
	public PageResponse<MatchResponse> matches(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(defaultValue = "0") @Min(0) @Max(100) int minScore,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return matchQueryService.matches(CurrentUser.id(jwt), minScore, page, size);
	}

}
