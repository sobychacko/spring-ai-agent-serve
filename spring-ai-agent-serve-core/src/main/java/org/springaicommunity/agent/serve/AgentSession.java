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

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.agent.serve.feedback.ServeQuestionHandler;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;

/**
 * Represents a client-to-agent session.
 *
 * <p>
 * Each session binds a transport connection to a {@link ChatClient} with isolated memory.
 * A single-threaded executor ensures that concurrent requests to the same session are
 * processed sequentially, preventing memory corruption and interleaved tool executions.
 *
 * <p>
 * The {@link #submitStream(AgentRequest, Consumer)} method accepts a callback that
 * receives {@link AgentEvent} instances as the agent produces them. The actual LLM work
 * runs on this session's dedicated executor thread, which calls {@code .blockLast()} on
 * the ChatClient stream, holding the thread until the request completes (including all
 * tool call recursions). This preserves the serial guarantee while enabling real-time
 * streaming to clients.
 *
 */
public class AgentSession {

	private static final Logger logger = LoggerFactory.getLogger(AgentSession.class);

	private final String sessionId;

	private final ChatClient chatClient;

	private final ChatMemory chatMemory;

	private final ExecutorService executor;

	private final ServeQuestionHandler questionHandler;

	private final ToolCallEventBridge toolCallEventBridge;

	public AgentSession(String sessionId, ChatClient chatClient, ChatMemory chatMemory) {
		this(sessionId, chatClient, chatMemory, null, null);
	}

	public AgentSession(String sessionId, ChatClient chatClient, ChatMemory chatMemory,
			ServeQuestionHandler questionHandler) {
		this(sessionId, chatClient, chatMemory, questionHandler, null);
	}

	public AgentSession(String sessionId, ChatClient chatClient, ChatMemory chatMemory,
			ServeQuestionHandler questionHandler, ToolCallEventBridge toolCallEventBridge) {
		this.sessionId = sessionId;
		this.chatClient = chatClient;
		this.chatMemory = chatMemory;
		this.questionHandler = questionHandler;
		this.toolCallEventBridge = toolCallEventBridge;
		this.executor = Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r, "agent-session-" + sessionId);
			t.setDaemon(true);
			return t;
		});
	}

	/**
	 * Submits a request for streaming processing. The provided {@code listener} receives
	 * {@link AgentEvent} instances as the agent produces them: text chunks, tool call
	 * lifecycle events, question events, and a final response or error.
	 *
	 * <p>
	 * The actual LLM work runs on this session's dedicated executor thread. The executor
	 * thread calls {@code .blockLast()} on the ChatClient stream, which holds the thread
	 * until the request completes (including all tool call recursions). This preserves
	 * serial execution: if a second request arrives while the first is still streaming, it
	 * queues behind the first on the single-threaded executor.
	 *
	 * <p>
	 * Tool call lifecycle events ({@code TOOL_CALL_STARTED}/{@code TOOL_CALL_COMPLETED})
	 * are emitted by {@link ObservableToolCallback} wrappers rather than from the stream
	 * itself, because Spring AI's model-level tool execution handles tool call chunks
	 * internally via {@code flatMap} — they never reach the subscriber's
	 * {@code doOnNext} handler.
	 * @param request the agent request
	 * @param listener callback that receives agent events as they are produced
	 */
	public void submitStream(AgentRequest request, Consumer<AgentEvent> listener) {
		this.executor.submit(() -> {
			try {
				if (this.questionHandler != null) {
					this.questionHandler.setEventCallback(listener);
				}
				if (this.toolCallEventBridge != null) {
					this.toolCallEventBridge.setEventCallback(listener);
				}

				logger.debug("Session [{}]: streaming message", this.sessionId);

				final StringBuilder accumulated = new StringBuilder();

				this.chatClient.prompt(request.message())
					.stream()
					.chatResponse()
					.doOnNext(chatResponse -> {
						if (chatResponse == null || chatResponse.getResult() == null) {
							return;
						}
						String text = chatResponse.getResult().getOutput().getText();
						if (text != null && !text.isEmpty()) {
							accumulated.append(text);
							listener.accept(AgentEvent.responseChunk(this.sessionId, text));
						}
					})
					.doOnComplete(
							() -> listener.accept(
									AgentEvent.finalResponse(this.sessionId, accumulated.toString())))
					.blockLast();
			}
			catch (Exception ex) {
				logger.error("Session [{}]: error processing message", this.sessionId, ex);
				listener.accept(AgentEvent.error(this.sessionId, ex.getMessage()));
			}
		});
	}

	/**
	 * Resolves a pending question by forwarding the answers to the question handler.
	 * @param questionId the question identifier
	 * @param answers the user's answers
	 * @throws IllegalStateException if no question handler is configured
	 */
	public void resolveQuestion(String questionId, Map<String, String> answers) {
		if (this.questionHandler == null) {
			throw new IllegalStateException("No question handler configured for session " + this.sessionId);
		}
		this.questionHandler.resolve(questionId, answers);
	}

	public String getSessionId() {
		return this.sessionId;
	}

	public ChatClient getChatClient() {
		return this.chatClient;
	}

	public ChatMemory getChatMemory() {
		return this.chatMemory;
	}

	/**
	 * Shuts down this session's executor. In-flight requests will complete, but no new
	 * requests will be accepted.
	 */
	public void close() {
		logger.debug("Session [{}]: closing", this.sessionId);
		this.executor.shutdown();
	}

}
