package de.zbidi.jobtracker.analysis;

import de.zbidi.jobtracker.ApiAuth;
import de.zbidi.jobtracker.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static de.zbidi.jobtracker.analysis.FakeChatModel.ANALYSIS_JSON;
import static de.zbidi.jobtracker.analysis.FakeChatModel.answer;
import static de.zbidi.jobtracker.analysis.FakeChatModel.callGetMySkills;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Everything real (security, Boot's ChatClient wiring, tool execution, database) except the model itself.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PostingAnalyzeApiIntegrationTest {

	@Autowired
	MockMvcTester mvc;

	@MockitoBean
	ChatModel chatModel;

	@BeforeEach
	void setUp() {
		given(chatModel.getOptions()).willReturn(ToolCallingChatOptions.builder().build());
	}

	@Test
	void analyzesAPostingAgainstTheCallersOwnSkills() {
		String alice = ApiAuth.registerNewUser(mvc, "analyze-alice");
		String bob = ApiAuth.registerNewUser(mvc, "analyze-bob");
		addSkill(alice, "Java", "EXPERT");
		addSkill(bob, "Haskell", "EXPERT");
		given(chatModel.call(any(Prompt.class))).willReturn(callGetMySkills(), answer(ANALYSIS_JSON));

		MvcTestResult result = mvc.post().uri("/api/postings/analyze").header(HttpHeaders.AUTHORIZATION, alice)
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"text": "Backend developer, Java and Spring required."}
						""").exchange();

		assertThat(result).hasStatus(HttpStatus.OK);
		assertThat(result).bodyJson().extractingPath("$.matchScore").isEqualTo(80);
		// the tool ran with Alice's id from the token: her skills went to the model, Bob's did not
		ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
		verify(chatModel, times(2)).call(prompts.capture());
		ToolResponseMessage toolResult = (ToolResponseMessage) prompts.getAllValues().get(1).getInstructions().getLast();
		assertThat(toolResult.getResponses().getFirst().responseData()).contains("Java").doesNotContain("Haskell");
	}

	@Test
	void analyzeNeedsAToken() {
		assertThat(mvc.post().uri("/api/postings/analyze").contentType(MediaType.APPLICATION_JSON)
				.content("{\"text\": \"x\"}").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	private void addSkill(String bearer, String name, String level) {
		assertThat(mvc.post().uri("/api/skills").header(HttpHeaders.AUTHORIZATION, bearer)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"%s\", \"years\": 3, \"level\": \"%s\"}".formatted(name, level)).exchange())
				.hasStatus(HttpStatus.CREATED);
	}

}
