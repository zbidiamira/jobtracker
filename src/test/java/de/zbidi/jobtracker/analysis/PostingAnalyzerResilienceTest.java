package de.zbidi.jobtracker.analysis;

import java.time.Duration;
import java.util.List;

import de.zbidi.jobtracker.skill.SkillRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.web.client.ResourceAccessException;

import static de.zbidi.jobtracker.analysis.FakeChatModel.answer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * What the caller gets when the model is slow, down, refuses the request or answers garbage.
 */
class PostingAnalyzerResilienceTest {

	ChatModel chatModel = FakeChatModel.create();
	SkillRepository skills = mock(SkillRepository.class);
	SimpleMeterRegistry meters = new SimpleMeterRegistry();

	PostingAnalyzer analyzer(Duration timeout) {
		given(skills.findByOwnerIdOrderByNameAsc(7L)).willReturn(List.of());
		return new PostingAnalyzer(PostingAnalyzer.createChatClient(ChatClient.builder(chatModel), new SkillTools(skills)), skills,
				meters, timeout);
	}

	@Test
	void slowModelTimesOut() {
		given(chatModel.call(any(Prompt.class))).willAnswer(invocation -> {
			Thread.sleep(5_000);
			return answer(FakeChatModel.ANALYSIS_JSON);
		});
		PostingAnalyzer analyzer = analyzer(Duration.ofMillis(200));

		long start = System.nanoTime();
		assertThatThrownBy(() -> analyzer.analyze(7L, "x"))
				.isInstanceOf(AiUnavailableException.class)
				.hasMessageContaining("did not answer within");
		assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));
		assertThat(meters.get("jobtracker.ai.duration").tag("outcome", "timeout").timer().count()).isEqualTo(1);
	}

	@Test
	void temporaryModelErrorMeansUnavailable() {
		given(chatModel.call(any(Prompt.class))).willThrow(new TransientAiException("503 from provider"));

		assertThatThrownBy(() -> analyzer(Duration.ofSeconds(5)).analyze(7L, "x"))
				.isInstanceOf(AiUnavailableException.class);
	}

	@Test
	void unreachableModelMeansUnavailable() {
		given(chatModel.call(any(Prompt.class))).willThrow(new ResourceAccessException("Connection refused"));

		assertThatThrownBy(() -> analyzer(Duration.ofSeconds(5)).analyze(7L, "x"))
				.isInstanceOf(AiUnavailableException.class);
	}

	/** 4xx from the provider (bad key, request too large): trying again can't help. */
	@Test
	void rejectedRequestIsNotRetried() {
		given(chatModel.call(any(Prompt.class))).willThrow(new NonTransientAiException("401 invalid x-api-key"));

		assertThatThrownBy(() -> analyzer(Duration.ofSeconds(5)).analyze(7L, "x"))
				.isInstanceOf(AiRejectedException.class);
		verify(chatModel, times(1)).call(any(Prompt.class));
	}

	@Test
	void answerThatIsNotJsonIsRejected() {
		given(chatModel.call(any(Prompt.class))).willReturn(answer("Sorry, I can't help with that."));

		assertThatThrownBy(() -> analyzer(Duration.ofSeconds(5)).analyze(7L, "x"))
				.isInstanceOf(AiRejectedException.class);
	}

	@Test
	void withoutAConfiguredModelTheAnalyzerIsUnavailable() {
		PostingAnalyzer analyzer = new PostingAnalyzer(null, skills, new SimpleMeterRegistry(),
				Duration.ofSeconds(5));

		assertThatThrownBy(() -> analyzer.analyze(7L, "x"))
				.isInstanceOf(AiUnavailableException.class)
				.hasMessageContaining("not configured");
	}

}
