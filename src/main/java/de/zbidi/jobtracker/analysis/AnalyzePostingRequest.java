package de.zbidi.jobtracker.analysis;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param text the job posting as pasted by the user; untrusted input, limited in length (cost and abuse)
 */
public record AnalyzePostingRequest(@NotBlank @Size(max = AnalyzePostingRequest.MAX_LENGTH) String text) {

	public static final int MAX_LENGTH = 10_000;

}
