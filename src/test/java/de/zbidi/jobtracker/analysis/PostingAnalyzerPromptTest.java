package de.zbidi.jobtracker.analysis;

import java.time.Duration;
import java.util.List;

import de.zbidi.jobtracker.skill.SkillRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;

import static de.zbidi.jobtracker.analysis.FakeChatModel.ANALYSIS_JSON;
import static de.zbidi.jobtracker.analysis.FakeChatModel.answer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * The instructions the model actually receives: the three steps and "only the structure".
 */
class PostingAnalyzerPromptTest {

	@Test
	void systemPromptExtractsComparesAndAnswersOnlyInTheStructure() {
		ChatModel chatModel = FakeChatModel.create();
		SkillRepository skills = mock(SkillRepository.class);
		given(skills.findByOwnerIdOrderByNameAsc(7L)).willReturn(List.of());
		given(chatModel.call(any(Prompt.class))).willReturn(answer(ANALYSIS_JSON));
		PostingAnalyzer analyzer = new PostingAnalyzer(PostingAnalyzer.createChatClient(ChatClient.builder(chatModel), new SkillTools(skills)),
				skills, new SimpleMeterRegistry(), Duration.ofSeconds(5));

		analyzer.analyze(7L, "We need Java.");

		ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
		verify(chatModel).call(prompt.capture());
		String system = prompt.getValue().getInstructions().stream()
				.filter(message -> message.getMessageType() == MessageType.SYSTEM)
				.map(Message::getText).reduce("", String::concat);
		assertThat(system)
				.contains("1. Extract the requirements")
				.contains("2. Compare them with the user's skills")
				.contains("getMySkills")
				.contains("Answer only with the requested structure")
				.contains("no text before or after it")
				.contains("untrusted");
	}

}
