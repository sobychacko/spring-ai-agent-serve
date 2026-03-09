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
package org.springaicommunity.agent.serve.transport.websocket;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.agent.serve.AgentRequest;
import org.springaicommunity.agent.serve.AgentSession;
import org.springaicommunity.agent.serve.AgentSessionManager;
import org.springaicommunity.agent.serve.QuestionAnswer;
import org.springaicommunity.agent.serve.transport.AgentEventSender;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

/**
 * WebSocket controller that handles incoming agent requests over STOMP.
 *
 * <p>
 * Receives messages on {@code /app/agent}, resolves or creates sessions, delegates to the
 * agent via streaming, and sends each event to the session-specific topic via
 * {@link AgentEventSender}.
 *
 * <p>
 * Also handles {@code /app/agent/answer} messages for resolving questions posed by the
 * agent through the {@code AskUserQuestionTool} bridge.
 *
 */
@Controller
public class AgentWebSocketController {

	private static final Logger logger = LoggerFactory.getLogger(AgentWebSocketController.class);

	private final AgentSessionManager sessionManager;

	private final AgentEventSender eventSender;

	public AgentWebSocketController(AgentSessionManager sessionManager, AgentEventSender eventSender) {
		this.sessionManager = sessionManager;
		this.eventSender = eventSender;
	}

	@MessageMapping("/agent")
	public void handle(AgentRequest request) {
		String sessionId = request.sessionId();
		if (sessionId == null || sessionId.isBlank()) {
			sessionId = UUID.randomUUID().toString();
		}

		logger.debug("Received message for session [{}]", sessionId);

		AgentSession session = this.sessionManager.getOrCreate(sessionId);
		final String resolvedSessionId = sessionId;

		// The actual LLM work runs on the session's dedicated executor thread;
		// events arrive here via the callback and are forwarded to the client.
		session.submitStream(new AgentRequest(resolvedSessionId, request.message()),
				event -> this.eventSender.send(resolvedSessionId, event));
	}

	@MessageMapping("/agent/answer")
	public void handleAnswer(QuestionAnswer answer) {
		logger.debug("Received answer for session [{}], question [{}]",
				answer.sessionId(), answer.questionId());

		this.sessionManager.get(answer.sessionId()).ifPresentOrElse(
				session -> session.resolveQuestion(answer.questionId(), answer.answers()),
				() -> logger.warn("Answer received for unknown session [{}], ignoring",
						answer.sessionId()));
	}

}
