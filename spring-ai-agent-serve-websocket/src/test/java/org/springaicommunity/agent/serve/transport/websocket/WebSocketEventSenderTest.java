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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springaicommunity.agent.serve.AgentEvent;

import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.mockito.Mockito.verify;

/**
 * Tests for {@link WebSocketEventSender}.
 */
@DisplayName("WebSocketEventSender Tests")
@ExtendWith(MockitoExtension.class)
class WebSocketEventSenderTest {

	@Mock
	private SimpMessagingTemplate messagingTemplate;

	@Test
	@DisplayName("Should send event to /topic/agent/{sessionId}")
	void shouldSendToCorrectDestination() {
		WebSocketEventSender sender = new WebSocketEventSender(this.messagingTemplate);
		AgentEvent event = AgentEvent.finalResponse("abc-123", "Hello!");

		sender.send("abc-123", event);

		verify(this.messagingTemplate).convertAndSend("/topic/agent/abc-123", event);
	}

}
