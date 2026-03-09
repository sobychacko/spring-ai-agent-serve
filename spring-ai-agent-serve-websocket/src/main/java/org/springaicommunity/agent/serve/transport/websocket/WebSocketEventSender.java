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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.agent.serve.AgentEvent;
import org.springaicommunity.agent.serve.transport.AgentEventSender;

import org.springframework.messaging.simp.SimpMessagingTemplate;

/**
 * WebSocket (STOMP) implementation of {@link AgentEventSender}.
 *
 * <p>
 * Sends agent events to the client's session-specific topic.
 *
 */
public class WebSocketEventSender implements AgentEventSender {

	private static final Logger logger = LoggerFactory.getLogger(WebSocketEventSender.class);

	private final SimpMessagingTemplate messagingTemplate;

	public WebSocketEventSender(SimpMessagingTemplate messagingTemplate) {
		this.messagingTemplate = messagingTemplate;
	}

	@Override
	public void send(String sessionId, AgentEvent event) {
		String destination = "/topic/agent/" + sessionId;
		logger.debug("Sending event [{}] to [{}]", event.type(), destination);
		this.messagingTemplate.convertAndSend(destination, event);
	}

}
