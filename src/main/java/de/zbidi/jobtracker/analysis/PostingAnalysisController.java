package de.zbidi.jobtracker.analysis;

import de.zbidi.jobtracker.auth.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** AI analysis of a pasted job posting against the caller's skills (/api/skills). */
@RestController
public class PostingAnalysisController {

	private final PostingAnalyzer analyzer;

	public PostingAnalysisController(PostingAnalyzer analyzer) {
		this.analyzer = analyzer;
	}

	@PostMapping("/api/postings/analyze")
	public PostingAnalysis analyze(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AnalyzePostingRequest request) {
		return analyzer.analyze(CurrentUser.id(jwt), request.text());
	}

}
