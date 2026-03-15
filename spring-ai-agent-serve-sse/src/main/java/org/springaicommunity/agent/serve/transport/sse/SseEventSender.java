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

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.agent.serve.AgentEvent;
import org.springaicommunity.agent.serve.transport.AgentEventSender;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE implementation of {@link AgentEventSender}.
 *
 * <p>
 * Maintains a map of session IDs to active {@link SseEmitter} instances. When an event
 * is sent, it is translated to an SSE event with the {@link AgentEvent#type()} as the
 * SSE event name, allowing browser clients to use
 * {@code eventSource.addEventListener("RESPONSE_CHUNK", ...)} for typed event handling.
 *
 * <p>
 * If sending fails (client disconnected), the emitter is removed automatically.
 *
 */
public class SseEventSender implements AgentEventSender {

	private static final Logger logger = LoggerFactory.getLogger(SseEventSender.class);

	private final ConcurrentMap<String, SseEmitter> emitters = new ConcurrentHashMap<>();

	@Override
	public void send(String sessionId, AgentEvent event) {
		SseEmitter emitter = this.emitters.get(sessionId);
		if (emitter != null) {
			try {
				emitter.send(SseEmitter.event()
					.name(event.type())
					.data(event));
			}
			catch (IOException ex) {
				logger.warn("Failed to send SSE event to session [{}], removing emitter", sessionId, ex);
				this.emitters.remove(sessionId);
			}
		}
		else {
			logger.warn("No SSE emitter for session [{}], event [{}] dropped", sessionId, event.type());
		}
	}

	/**
	 * Registers an {@link SseEmitter} for the given session, replacing any existing one.
	 * @param sessionId the session identifier
	 * @param emitter the SSE emitter for the client connection
	 */
	public void register(String sessionId, SseEmitter emitter) {
		this.emitters.put(sessionId, emitter);
		logger.debug("Registered SSE emitter for session [{}]", sessionId);
	}

	/**
	 * Removes the {@link SseEmitter} for the given session.
	 * @param sessionId the session identifier
	 */
	public void remove(String sessionId) {
		this.emitters.remove(sessionId);
		logger.debug("Removed SSE emitter for session [{}]", sessionId);
	}

}
