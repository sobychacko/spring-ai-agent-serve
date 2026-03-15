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
package org.springaicommunity.agent.serve.transport.sse;

import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.agent.serve.AgentEvent;
import org.springaicommunity.agent.serve.AgentRequest;
import org.springaicommunity.agent.serve.AgentSession;
import org.springaicommunity.agent.serve.AgentSessionManager;
import org.springaicommunity.agent.serve.QuestionAnswer;
import org.springaicommunity.agent.serve.config.AgentSseProperties;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * REST controller that serves agents over Server-Sent Events.
 *
 * <p>
 * Provides three endpoints:
 * <ul>
 *   <li>{@code GET /api/agent/sessions/{sessionId}/events} — opens an SSE stream for
 *       receiving agent events. If {@code sessionId} is {@code "new"}, a UUID is generated
 *       and sent as the first event.</li>
 *   <li>{@code POST /api/agent/sessions/{sessionId}/messages} — sends a user message to
 *       the agent. Returns {@code 202 Accepted}.</li>
 *   <li>{@code POST /api/agent/sessions/{sessionId}/answers} — resolves a pending question
 *       from the agent's {@code AskUserQuestionTool}.</li>
 * </ul>
 *
 * <p>
 * SSE is inherently half-duplex (server-to-client only), so client-to-server communication
 * uses standard REST POST endpoints. This is simpler than WebSocket/STOMP and works through
 * proxies and load balancers more easily.
 *
 */
@RestController
@RequestMapping("/api/agent/sessions")
public class AgentSseController {

	private static final Logger logger = LoggerFactory.getLogger(AgentSseController.class);

	private final AgentSessionManager sessionManager;

	private final SseEventSender eventSender;

	private final long emitterTimeoutMs;

	public AgentSseController(AgentSessionManager sessionManager, SseEventSender eventSender,
			AgentSseProperties properties) {
		this.sessionManager = sessionManager;
		this.eventSender = eventSender;
		this.emitterTimeoutMs = properties.getEmitterTimeoutMs();
	}

	/**
	 * Opens an SSE event stream for the given session. If {@code sessionId} is
	 * {@code "new"}, a UUID is generated and sent as the first SSE event so the client
	 * learns its assigned session ID.
	 * @param sessionId the session identifier, or {@code "new"} to create one
	 * @return an {@link SseEmitter} streaming agent events
	 */
	@GetMapping(value = "/{sessionId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter connect(@PathVariable String sessionId) {
		String resolvedSessionId = sessionId;
		if ("new".equals(sessionId)) {
			resolvedSessionId = UUID.randomUUID().toString();
		}

		logger.debug("SSE connection opened for session [{}]", resolvedSessionId);

		SseEmitter emitter = new SseEmitter(this.emitterTimeoutMs);
		final String sid = resolvedSessionId;

		this.eventSender.register(sid, emitter);
		emitter.onCompletion(() -> this.eventSender.remove(sid));
		emitter.onTimeout(() -> this.eventSender.remove(sid));
		emitter.onError(ex -> this.eventSender.remove(sid));

		this.sessionManager.getOrCreate(sid);

		// If the client requested "new", send the generated session ID as the first event
		if ("new".equals(sessionId)) {
			this.eventSender.send(sid,
					new AgentEvent(sid, "SESSION_CREATED", null, Map.of("sessionId", sid)));
		}

		return emitter;
	}

	/**
	 * Accepts a user message and submits it to the agent for streaming processing. Events
	 * are delivered to the client via the SSE stream opened by {@link #connect}.
	 * @param sessionId the session identifier
	 * @param request the agent request containing the user's message
	 * @return {@code 202 Accepted} with the session ID
	 */
	@PostMapping("/{sessionId}/messages")
	public ResponseEntity<Map<String, String>> sendMessage(@PathVariable String sessionId,
			@RequestBody AgentRequest request) {
		logger.debug("Received message for session [{}]", sessionId);

		AgentSession session = this.sessionManager.getOrCreate(sessionId);
		session.submitStream(new AgentRequest(sessionId, request.message()),
				event -> this.eventSender.send(sessionId, event));

		return ResponseEntity.accepted().body(Map.of("sessionId", sessionId));
	}

	/**
	 * Resolves a pending question posed by the agent's {@code AskUserQuestionTool}.
	 * @param sessionId the session identifier
	 * @param answer the question answer containing question ID and answers
	 * @return {@code 204 No Content} on success, {@code 404 Not Found} if session unknown
	 */
	@PostMapping("/{sessionId}/answers")
	public ResponseEntity<Void> sendAnswer(@PathVariable String sessionId,
			@RequestBody QuestionAnswer answer) {
		logger.debug("Received answer for session [{}], question [{}]", sessionId, answer.questionId());

		return this.sessionManager.get(sessionId)
			.map(session -> {
				session.resolveQuestion(answer.questionId(), answer.answers());
				return ResponseEntity.noContent().<Void>build();
			})
			.orElseGet(() -> {
				logger.warn("Answer received for unknown session [{}], ignoring", sessionId);
				return ResponseEntity.notFound().build();
			});
	}

}
