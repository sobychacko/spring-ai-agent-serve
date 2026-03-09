/*
 * Copyright 2025-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springaicommunity.agent.serve.feedback;

import org.springaicommunity.agent.tools.AskUserQuestionTool;

import org.springframework.ai.chat.client.ChatClient;

/**
 * Factory for creating per-session {@link ServeQuestionHandler} instances and wiring
 * the {@link AskUserQuestionTool} onto the session's {@link ChatClient.Builder}.
 *
 * <p>
 * Each session gets its own handler so that question state (pending futures, callbacks)
 * is isolated per session. The factory is a singleton bean; the handlers it creates are
 * per-session objects.
 *
 * <p>
 * This factory is only instantiated when {@code AskUserQuestionTool} is on the classpath
 * (guarded by {@code @ConditionalOnClass} in the auto-configuration), so the direct
 * reference to the optional dependency class is safe.
 *
 */
public class ServeQuestionHandlerFactory {

	private final long timeoutMinutes;

	public ServeQuestionHandlerFactory(long timeoutMinutes) {
		this.timeoutMinutes = timeoutMinutes;
	}

	/**
	 * Creates a new question handler bound to the given session.
	 * @param sessionId the session identifier
	 * @return a new handler
	 */
	public ServeQuestionHandler create(String sessionId) {
		return new ServeQuestionHandler(sessionId, this.timeoutMinutes);
	}

	/**
	 * Configures the given {@link ChatClient.Builder} with an
	 * {@link AskUserQuestionTool} backed by the provided handler.
	 * <p>
	 * This method centralizes the reference to the optional {@code AskUserQuestionTool}
	 * class so that callers (e.g., {@code InMemoryAgentSessionManager}) don't need a
	 * compile-time dependency on agent-utils.
	 * @param builder the chat client builder to configure
	 * @param handler the per-session question handler
	 */
	public void configureBuilder(ChatClient.Builder builder, ServeQuestionHandler handler) {
		AskUserQuestionTool askTool = AskUserQuestionTool.builder()
			.questionHandler(handler)
			.answersValidation(false)
			.build();
		builder.defaultTools(askTool);
	}

}
