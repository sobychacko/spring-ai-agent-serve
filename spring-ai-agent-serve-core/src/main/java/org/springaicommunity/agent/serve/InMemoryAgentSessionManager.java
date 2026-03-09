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
package org.springaicommunity.agent.serve;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.agent.serve.feedback.ServeQuestionHandler;
import org.springaicommunity.agent.serve.feedback.ServeQuestionHandlerFactory;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.tool.ToolCallback;

/**
 * In-memory implementation of {@link AgentSessionManager}.
 *
 * <p>
 * Creates sessions by cloning the application's {@link ChatClient.Builder} to ensure each
 * session has isolated memory and advisor state. When a
 * {@link ServeQuestionHandlerFactory} is provided, each session is also wired with a
 * per-session {@link ServeQuestionHandler} so that the agent can ask the user questions
 * through the serve layer transport.
 *
 * <p>
 * When tool callbacks are provided via the constructor, they are wrapped with
 * {@link ObservableToolCallback} during session creation so that tool call lifecycle
 * events ({@code TOOL_CALL_STARTED}/{@code TOOL_CALL_COMPLETED}) are emitted to the
 * client in real time.
 *
 */
public class InMemoryAgentSessionManager implements AgentSessionManager {

	private static final Logger logger = LoggerFactory.getLogger(InMemoryAgentSessionManager.class);

	private final ChatClient.Builder chatClientBuilder;

	private final ConcurrentMap<String, AgentSession> sessions = new ConcurrentHashMap<>();

	private final int maxMessages;

	private final ServeQuestionHandlerFactory questionHandlerFactory;

	private final List<ToolCallback> toolCallbacks;

	public InMemoryAgentSessionManager(ChatClient.Builder chatClientBuilder) {
		this(chatClientBuilder, 500, null, null);
	}

	public InMemoryAgentSessionManager(ChatClient.Builder chatClientBuilder, int maxMessages) {
		this(chatClientBuilder, maxMessages, null, null);
	}

	public InMemoryAgentSessionManager(ChatClient.Builder chatClientBuilder, int maxMessages,
			ServeQuestionHandlerFactory questionHandlerFactory) {
		this(chatClientBuilder, maxMessages, questionHandlerFactory, null);
	}

	public InMemoryAgentSessionManager(ChatClient.Builder chatClientBuilder, int maxMessages,
			ServeQuestionHandlerFactory questionHandlerFactory, List<ToolCallback> toolCallbacks) {
		this.chatClientBuilder = chatClientBuilder;
		this.maxMessages = maxMessages;
		this.questionHandlerFactory = questionHandlerFactory;
		this.toolCallbacks = (toolCallbacks != null) ? Collections.unmodifiableList(toolCallbacks)
				: Collections.emptyList();
	}

	@Override
	public Optional<AgentSession> get(String sessionId) {
		return Optional.ofNullable(this.sessions.get(sessionId));
	}

	@Override
	public AgentSession getOrCreate(String sessionId) {
		return this.sessions.computeIfAbsent(sessionId, this::createSession);
	}

	@Override
	public void destroy(String sessionId) {
		AgentSession session = this.sessions.remove(sessionId);
		if (session != null) {
			logger.info("Destroying session [{}]", sessionId);
			session.close();
		}
	}

	@Override
	public Collection<String> activeSessions() {
		return List.copyOf(this.sessions.keySet());
	}

	private AgentSession createSession(String sessionId) {
		logger.info("Creating new session [{}]", sessionId);

		ChatMemory memory = MessageWindowChatMemory.builder()
			.maxMessages(this.maxMessages)
			.build();

		ServeQuestionHandler questionHandler = null;
		ToolCallEventBridge toolCallEventBridge = null;

		ChatClient.Builder builder = this.chatClientBuilder.clone()
			.defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).conversationId(sessionId).build());

		// Wrap tool callbacks with ObservableToolCallback so that tool call
		// lifecycle events are emitted to the client in real time.
		if (!this.toolCallbacks.isEmpty()) {
			toolCallEventBridge = new ToolCallEventBridge(sessionId);
			ToolCallEventBridge bridge = toolCallEventBridge;
			ToolCallback[] wrapped = this.toolCallbacks.stream()
				.map(tc -> (ToolCallback) new ObservableToolCallback(tc, bridge))
				.toArray(ToolCallback[]::new);
			builder.defaultToolCallbacks(wrapped);
			logger.debug("Session [{}]: {} tools wrapped with event observation", sessionId,
					this.toolCallbacks.size());
		}

		// When the question handler factory is present (agent-utils on classpath),
		// create a per-session handler and wire it as a default tool on the builder.
		// The factory handles the AskUserQuestionTool reference internally so that
		// this class has no compile-time dependency on the optional agent-utils module.
		if (this.questionHandlerFactory != null) {
			questionHandler = this.questionHandlerFactory.create(sessionId);
			this.questionHandlerFactory.configureBuilder(builder, questionHandler);
			logger.debug("Session [{}]: AskUserQuestionTool wired with serve handler", sessionId);
		}

		ChatClient client = builder.build();

		return new AgentSession(sessionId, client, memory, questionHandler, toolCallEventBridge);
	}

}
