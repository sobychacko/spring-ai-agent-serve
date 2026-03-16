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
package org.springaicommunity.agent.serve.transport.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.agent.serve.AgentRequest;
import org.springaicommunity.agent.serve.AgentSession;
import org.springaicommunity.agent.serve.AgentSessionManager;
import org.springaicommunity.agent.serve.QuestionAnswer;
import org.springaicommunity.agent.serve.transport.AgentEventSender;

import org.springframework.kafka.annotation.KafkaListener;

/**
 * Kafka consumer that routes inbound messages to agent sessions.
 *
 * <p>
 * Listens on two topics:
 * <ul>
 *   <li>Requests topic — receives {@link AgentRequest} messages, creates or retrieves
 *       the session, and submits the request for streaming processing.</li>
 *   <li>Answers topic — receives {@link QuestionAnswer} messages and resolves pending
 *       questions on the corresponding session.</li>
 * </ul>
 *
 * <p>
 * This is the Kafka equivalent of the SSE controller's {@code sendMessage()} and
 * {@code sendAnswer()} endpoints. The session management, event model, and agent
 * execution are identical — only the inbound transport differs.
 *
 */
public class KafkaAgentListener {

	private static final Logger logger = LoggerFactory.getLogger(KafkaAgentListener.class);

	private final AgentSessionManager sessionManager;

	private final AgentEventSender eventSender;

	public KafkaAgentListener(AgentSessionManager sessionManager, AgentEventSender eventSender) {
		this.sessionManager = sessionManager;
		this.eventSender = eventSender;
	}

	@KafkaListener(topics = "${spring.ai.agent.serve.kafka.requests-topic:agent-requests}",
			groupId = "${spring.ai.agent.serve.kafka.consumer-group:agent-serve}",
			containerFactory = "agentServeKafkaListenerContainerFactory")
	public void onRequest(AgentRequest request) {
		String sessionId = request.sessionId();
		logger.debug("Received request from Kafka for session [{}]", sessionId);

		AgentSession session = this.sessionManager.getOrCreate(sessionId);
		session.submitStream(new AgentRequest(sessionId, request.message()),
				event -> this.eventSender.send(sessionId, event));
	}

	@KafkaListener(topics = "${spring.ai.agent.serve.kafka.answers-topic:agent-answers}",
			groupId = "${spring.ai.agent.serve.kafka.consumer-group:agent-serve}",
			containerFactory = "agentServeKafkaListenerContainerFactory")
	public void onAnswer(QuestionAnswer answer) {
		String sessionId = answer.sessionId();
		logger.debug("Received answer from Kafka for session [{}], question [{}]",
				sessionId, answer.questionId());

		this.sessionManager.get(sessionId).ifPresentOrElse(
				session -> session.resolveQuestion(answer.questionId(), answer.answers()),
				() -> logger.warn("Answer received for unknown session [{}], ignoring", sessionId));
	}

}
