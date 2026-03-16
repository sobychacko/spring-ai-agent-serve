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

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.agent.serve.AgentEvent;
import org.springaicommunity.agent.serve.metrics.AgentServeMetrics;
import org.springaicommunity.agent.tools.AskUserQuestionTool;

/**
 * Bridges the {@link AskUserQuestionTool} with the serve layer's event-based transport.
 *
 * <p>
 * When the agent calls {@code AskUserQuestionTool}, Spring AI invokes this handler's
 * {@link #handle(List)} method on the session's executor thread (inside the streaming
 * {@code .blockLast()} call). The handler:
 * <ol>
 *   <li>Generates a unique questionId</li>
 *   <li>Pushes a {@code QUESTION_REQUIRED} event via the callback to the client</li>
 *   <li>Blocks on a {@link CompletableFuture} until the client answers or timeout</li>
 * </ol>
 *
 * <p>
 * The WebSocket controller receives the answer and calls {@link #resolve(String, Map)}
 * which completes the future, unblocking the handler and allowing the agent stream to
 * continue.
 *
 * <p>
 * Each instance is bound to a single session.
 *
 */
public class ServeQuestionHandler implements AskUserQuestionTool.QuestionHandler {

	private static final Logger logger = LoggerFactory.getLogger(ServeQuestionHandler.class);

	private final String sessionId;

	private final long timeoutMinutes;

	private final AgentServeMetrics metrics;

	private final ConcurrentHashMap<String, CompletableFuture<Map<String, String>>> pendingQuestions = new ConcurrentHashMap<>();

	private volatile Consumer<AgentEvent> eventCallback;

	public ServeQuestionHandler(String sessionId, long timeoutMinutes) {
		this(sessionId, timeoutMinutes, null);
	}

	public ServeQuestionHandler(String sessionId, long timeoutMinutes, AgentServeMetrics metrics) {
		this.sessionId = sessionId;
		this.timeoutMinutes = timeoutMinutes;
		this.metrics = metrics;
	}

	/**
	 * Sets the callback used to push events to the client. Must be called before each
	 * stream request so the handler can emit {@code QUESTION_REQUIRED} events through the
	 * active sink.
	 * @param eventCallback the callback to push events
	 */
	public void setEventCallback(Consumer<AgentEvent> eventCallback) {
		this.eventCallback = eventCallback;
	}

	/**
	 * Called by Spring AI when the agent invokes {@code AskUserQuestionTool}. Runs on the
	 * session's executor thread. Pushes a {@code QUESTION_REQUIRED} event to the client
	 * and blocks until the client answers or timeout expires.
	 * @param questions the questions to present to the user
	 * @return the user's answers keyed by question text
	 */
	@Override
	public Map<String, String> handle(List<AskUserQuestionTool.Question> questions) {
		String questionId = UUID.randomUUID().toString();
		CompletableFuture<Map<String, String>> future = new CompletableFuture<>();
		this.pendingQuestions.put(questionId, future);

		try {
			logger.debug("Session [{}]: question [{}] sent to client, waiting for answer",
					this.sessionId, questionId);

			Consumer<AgentEvent> callback = this.eventCallback;
			if (callback != null) {
				callback.accept(AgentEvent.questionRequired(this.sessionId, questionId, questions));
			}
			else {
				logger.warn("Session [{}]: no event callback set, cannot deliver question [{}]",
						this.sessionId, questionId);
			}

			return future.get(this.timeoutMinutes, TimeUnit.MINUTES);
		}
		catch (TimeoutException ex) {
			logger.warn("Session [{}]: question [{}] timed out after {} minutes",
					this.sessionId, questionId, this.timeoutMinutes);
			if (this.metrics != null) {
				this.metrics.questionTimedOut();
			}
			throw new RuntimeException("Question timed out after " + this.timeoutMinutes + " minutes");
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Question handling interrupted", ex);
		}
		catch (Exception ex) {
			throw new RuntimeException("Failed to get answer for question " + questionId, ex);
		}
		finally {
			this.pendingQuestions.remove(questionId);
		}
	}

	/**
	 * Resolves a pending question with the user's answers. Called by the WebSocket
	 * controller when it receives a {@code QuestionAnswer} message from the client.
	 * @param questionId the question identifier
	 * @param answers the user's answers
	 */
	public void resolve(String questionId, Map<String, String> answers) {
		CompletableFuture<Map<String, String>> future = this.pendingQuestions.get(questionId);
		if (future != null) {
			logger.debug("Session [{}]: resolving question [{}]", this.sessionId, questionId);
			future.complete(answers);
		}
		else {
			logger.warn("Session [{}]: no pending question found for [{}]", this.sessionId, questionId);
		}
	}

}
