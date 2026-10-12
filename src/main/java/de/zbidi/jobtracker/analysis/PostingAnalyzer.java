package de.zbidi.jobtracker.analysis;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import de.zbidi.jobtracker.skill.Skill;
import de.zbidi.jobtracker.skill.SkillRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PreDestroy;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;

/**
 * Analyzes a job posting against the caller's skills with an LLM.
 * <ul>
 *     <li>prompt injection: the instructions are a fixed system prompt; the posting goes verbatim (no template
 *     rendering) into the user message between {@code <posting>} tags, which it can't open or close itself; the
 *     answer is checked afterwards (score range, matched skills ⊆ the user's skills)</li>
 *     <li>the user's skills come from the tool {@link SkillTools#getMySkills}, scoped by the server-side tool
 *     context</li>
 *     <li>resilience: hard timeout, unavailable → {@link AiUnavailableException} (503), rejected or unusable →
 *     {@link AiRejectedException} (502); retries are Spring AI's ({@code spring.ai.retry.*}), never on 4xx</li>
 *     <li>token usage is logged per request and counted in {@code jobtracker.ai.tokens}; the duration per outcome
 *     (success, timeout, unavailable, rejected) is timed in {@code jobtracker.ai.duration}</li>
 * </ul>
 */
@Service
public class PostingAnalyzer {

	static final String TOKENS_METRIC = "jobtracker.ai.tokens";
	static final String DURATION_METRIC = "jobtracker.ai.duration";
	private static final List<String> OUTCOMES = List.of("success", "timeout", "unavailable", "rejected");

	private static final Logger log = LoggerFactory.getLogger(PostingAnalyzer.class);
	private static final Pattern POSTING_TAG = Pattern.compile("</?\\s*posting\\s*>", Pattern.CASE_INSENSITIVE);
	private static final String SYSTEM_PROMPT = load("prompts/posting-analyzer-system.st");

	private final @Nullable ChatClient chatClient;
	private final SkillRepository skillRepository;
	private final MeterRegistry meters;
	private final Duration timeout;
	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

	/** No chat model ({@code spring.ai.model.chat=none}): the app runs, the analyzer answers 503. */
	@Autowired
	public PostingAnalyzer(ObjectProvider<ChatModel> chatModel, ObjectProvider<ChatClient.Builder> chatClientBuilder,
			SkillTools skillTools, SkillRepository skillRepository, MeterRegistry meters,
			@Value("${jobtracker.ai.timeout}") Duration timeout) {
		this(chatModel.getIfAvailable() == null ? null : createChatClient(chatClientBuilder.getObject(), skillTools),
				skillRepository, meters, timeout);
	}

	PostingAnalyzer(@Nullable ChatClient chatClient, SkillRepository skillRepository, MeterRegistry meters,
			Duration timeout) {
		this.chatClient = chatClient;
		this.skillRepository = skillRepository;
		this.meters = meters;
		this.timeout = timeout;
		// registered up front, so /actuator/metrics lists them before the first analysis
		for (String type : List.of("prompt", "completion")) {
			Counter.builder(TOKENS_METRIC).tag("type", type).baseUnit("tokens")
					.description("Tokens used by posting analyses").register(meters);
		}
		for (String outcome : OUTCOMES) {
			Timer.builder(DURATION_METRIC).tag("outcome", outcome)
					.description("Duration of posting analyses, including tool calls").register(meters);
		}
	}

	/**
	 * The ChatClient for analyses: the fixed instructions and the getMySkills tool are defaults, so every call gets
	 * them. Per call only the posting and the tool context (whose skills) are added.
	 */
	static ChatClient createChatClient(ChatClient.Builder builder, SkillTools skillTools) {
		return builder.defaultSystem(SYSTEM_PROMPT).defaultTools(skillTools).build();
	}

	/** False without a configured chat model ({@code spring.ai.model.chat=none}). */
	public boolean isAvailable() {
		return chatClient != null;
	}

	public PostingAnalysis analyze(long ownerId, String postingText) {
		if (chatClient == null) {
			throw new AiUnavailableException("AI is not configured (set a profile: local or cloud)");
		}
		Timer.Sample sample = Timer.start(meters);
		String outcome = "success";
		try {
			ResponseEntity<ChatResponse, PostingAnalysis> response = callWithTimeout(chatClient, ownerId, postingText);
			recordUsage(response.response());
			PostingAnalysis analysis = response.entity();
			if (analysis == null) {
				throw new AiRejectedException("The AI model returned no analysis", null);
			}
			return guard(analysis, ownerId);
		}
		catch (AiUnavailableException ex) {
			outcome = ex.getCause() instanceof TimeoutException ? "timeout" : "unavailable";
			throw ex;
		}
		catch (AiRejectedException ex) {
			outcome = "rejected";
			throw ex;
		}
		finally {
			sample.stop(meters.timer(DURATION_METRIC, "outcome", outcome));
		}
	}

	private ResponseEntity<ChatResponse, PostingAnalysis> callWithTimeout(ChatClient client, long ownerId,
			String postingText) {
		CompletableFuture<ResponseEntity<ChatResponse, PostingAnalysis>> call = CompletableFuture.supplyAsync(
				() -> client.prompt()
						// no template parameters: the text is not rendered, "{...}" in a posting stays plain text
						.user("<posting>\n" + neutralize(postingText) + "\n</posting>")
						// whose skills getMySkills returns; seen only by the tool, never sent to the model
						.toolContext(Map.of(SkillTools.USER_ID, ownerId))
						.call()
						.responseEntity(PostingAnalysis.class),
				executor);
		try {
			return call.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
		}
		catch (TimeoutException ex) {
			call.cancel(true);
			throw new AiUnavailableException("The AI model did not answer within " + timeout.toSeconds() + "s", ex);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new AiUnavailableException("Interrupted while waiting for the AI model", ex);
		}
		catch (ExecutionException ex) {
			throw translate(ex.getCause());
		}
	}

	private static RuntimeException translate(Throwable failure) {
		for (Throwable t = failure; t != null; t = t.getCause()) {
			if (t instanceof NonTransientAiException) {
				log.warn("AI request rejected: {}", t.getMessage());
				return new AiRejectedException("The AI provider rejected the request", t);
			}
			if (t instanceof TransientAiException || t instanceof ResourceAccessException
					|| t instanceof IOException) {
				log.warn("AI model unavailable: {}", t.getMessage());
				return new AiUnavailableException("The AI model is not available", t);
			}
		}
		// e.g. the answer wasn't the requested JSON
		log.warn("Unusable AI answer", failure);
		return new AiRejectedException("The AI model returned an unusable answer", failure);
	}

	/** A posting can't open or close the delimiter itself, so it can't place text outside of it. */
	private static String neutralize(String postingText) {
		return POSTING_TAG.matcher(postingText).replaceAll("[tag removed]");
	}

	/** The answer is untrusted too: keep it in range and consistent with the user's real skills. */
	private PostingAnalysis guard(PostingAnalysis analysis, long ownerId) {
		Set<String> userSkills = skillRepository.findByOwnerIdOrderByNameAsc(ownerId).stream()
				.map(Skill::getName).map(PostingAnalyzer::normalize).collect(Collectors.toSet());
		List<String> matched = new ArrayList<>();
		List<String> missing = new ArrayList<>(orEmpty(analysis.missingSkills()));
		for (String skill : orEmpty(analysis.matchedSkills())) {
			if (userSkills.contains(normalize(skill))) {
				matched.add(skill);
			}
			else if (missing.stream().noneMatch(m -> normalize(m).equals(normalize(skill)))) {
				missing.add(skill);
			}
		}
		int score = analysis.matchScore() == null ? 0 : Math.clamp(analysis.matchScore(), 0, 100);
		return new PostingAnalysis(orEmpty(analysis.title()), orEmpty(analysis.mustHave()),
				orEmpty(analysis.niceToHave()), List.copyOf(matched), List.copyOf(missing), score,
				orEmpty(analysis.summary()));
	}

	private void recordUsage(@Nullable ChatResponse response) {
		Usage usage = response == null ? null : response.getMetadata().getUsage();
		int prompt = usage == null || usage.getPromptTokens() == null ? 0 : usage.getPromptTokens();
		int completion = usage == null || usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens();
		// as reported by the provider (may include e.g. cached or reasoning tokens)
		int total = usage == null || usage.getTotalTokens() == null ? prompt + completion : usage.getTotalTokens();
		log.info("ai tokens prompt={} completion={} total={}", prompt, completion, total);
		meters.counter(TOKENS_METRIC, "type", "prompt").increment(prompt);
		meters.counter(TOKENS_METRIC, "type", "completion").increment(completion);
	}

	@PreDestroy
	void shutdown() {
		executor.shutdownNow();
	}

	private static String normalize(String skill) {
		return skill.strip().toLowerCase(Locale.ROOT);
	}

	private static List<String> orEmpty(@Nullable List<String> list) {
		return list == null ? List.of() : list.stream().filter(s -> s != null && !s.isBlank()).toList();
	}

	private static String orEmpty(@Nullable String text) {
		return text == null ? "" : text;
	}

	private static String load(String resource) {
		try {
			return new ClassPathResource(resource).getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
