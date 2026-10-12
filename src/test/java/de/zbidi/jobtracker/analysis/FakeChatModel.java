package de.zbidi.jobtracker.analysis;

import java.util.List;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Builds a mocked ChatModel and the answers it gives. No real model is ever called in tests.
 */
final class FakeChatModel {

	static final String ANALYSIS_JSON = """
			{"title": "Backend Developer", "mustHave": ["Java", "Spring"], "niceToHave": ["Kafka"],
			 "matchedSkills": ["Java", "Spring"], "missingSkills": ["Kafka"], "matchScore": 80,
			 "summary": "Strong match."}
			""";

	private FakeChatModel() {
	}

	/**
	 * Real providers return tool-calling options; ChatClient only attaches tools and tool context when the model's
	 * options support them.
	 */
	static ChatModel create() {
		ChatModel chatModel = mock(ChatModel.class);
		given(chatModel.getOptions()).willReturn(ToolCallingChatOptions.builder().build());
		return chatModel;
	}

	static ChatResponse answer(String json, int promptTokens, int completionTokens) {
		return new ChatResponse(List.of(new Generation(new AssistantMessage(json))),
				ChatResponseMetadata.builder().usage(new DefaultUsage(promptTokens, completionTokens)).build());
	}

	/** With a total from the provider that isn't simply prompt + completion (e.g. cached or reasoning tokens). */
	static ChatResponse answer(String json, int promptTokens, int completionTokens, int totalTokens) {
		return new ChatResponse(List.of(new Generation(new AssistantMessage(json))), ChatResponseMetadata.builder()
				.usage(new DefaultUsage(promptTokens, completionTokens, totalTokens)).build());
	}

	static ChatResponse answer(String json) {
		return answer(json, 100, 50);
	}

	/** The model asks to run getMySkills before answering. */
	static ChatResponse callGetMySkills() {
		AssistantMessage toolCall = AssistantMessage.builder()
				.toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", "getMySkills", "{}")))
				.build();
		return new ChatResponse(List.of(new Generation(toolCall)),
				ChatResponseMetadata.builder().usage(new DefaultUsage(30, 5)).build());
	}

}
