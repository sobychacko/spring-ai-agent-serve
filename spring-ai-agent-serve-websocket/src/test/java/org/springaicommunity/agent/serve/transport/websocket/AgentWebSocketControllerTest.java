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

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springaicommunity.agent.serve.AgentEvent;
import org.springaicommunity.agent.serve.AgentRequest;
import org.springaicommunity.agent.serve.AgentSession;
import org.springaicommunity.agent.serve.AgentSessionManager;
import org.springaicommunity.agent.serve.QuestionAnswer;
import org.springaicommunity.agent.serve.transport.AgentEventSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link AgentWebSocketController}.
 */
@DisplayName("AgentWebSocketController Tests")
@ExtendWith(MockitoExtension.class)
class AgentWebSocketControllerTest {

	@Mock
	private AgentSessionManager sessionManager;

	@Mock
	private AgentEventSender eventSender;

	@Mock
	private AgentSession session;

	private AgentWebSocketController controller;

	@BeforeEach
	void setUp() {
		this.controller = new AgentWebSocketController(this.sessionManager, this.eventSender);
	}

	@Nested
	@DisplayName("Session ID Resolution")
	class SessionIdResolution {

		@Test
		@DisplayName("Should use provided sessionId")
		void shouldUseProvidedSessionId() {
			when(sessionManager.getOrCreate("user-123")).thenReturn(session);

			controller.handle(new AgentRequest("user-123", "hello"));

			verify(sessionManager).getOrCreate("user-123");
		}

		@Test
		@DisplayName("Should generate UUID when sessionId is null")
		void shouldGenerateUuidWhenNull() {
			when(sessionManager.getOrCreate(anyString())).thenReturn(session);

			controller.handle(new AgentRequest(null, "hello"));

			ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
			verify(sessionManager).getOrCreate(captor.capture());
			assertThatNoException().isThrownBy(() -> UUID.fromString(captor.getValue()));
		}

	}

	@Nested
	@DisplayName("Streaming Response Delivery")
	class StreamingResponseDelivery {

		@Test
		@DisplayName("Should send each streaming event via eventSender")
		@SuppressWarnings("unchecked")
		void shouldSendStreamingEvents() {
			AgentEvent chunk = AgentEvent.responseChunk("my-session", "Hello");
			AgentEvent finalEvent = AgentEvent.finalResponse("my-session", "Hello");
			when(sessionManager.getOrCreate("my-session")).thenReturn(session);

			// Simulate submitStream calling the listener with events
			doAnswer(invocation -> {
				Consumer<AgentEvent> listener = invocation.getArgument(1);
				listener.accept(chunk);
				listener.accept(finalEvent);
				return null;
			}).when(session).submitStream(any(AgentRequest.class), any(Consumer.class));

			controller.handle(new AgentRequest("my-session", "Hi"));

			verify(eventSender).send(eq("my-session"), eq(chunk));
			verify(eventSender).send(eq("my-session"), eq(finalEvent));
		}

	}

	@Nested
	@DisplayName("Answer Routing")
	class AnswerRouting {

		@Test
		@DisplayName("Should route answer to session's resolveQuestion")
		void shouldRouteAnswerToSession() {
			when(sessionManager.get("s1")).thenReturn(Optional.of(session));
			Map<String, String> answers = Map.of("Which?", "Option A");

			controller.handleAnswer(new QuestionAnswer("s1", "q-123", answers));

			verify(session).resolveQuestion("q-123", answers);
		}

		@Test
		@DisplayName("Should ignore answer for unknown session without creating zombie")
		void shouldIgnoreAnswerForUnknownSession() {
			when(sessionManager.get("unknown")).thenReturn(Optional.empty());

			controller.handleAnswer(new QuestionAnswer("unknown", "q-123", Map.of()));

			verify(sessionManager, never()).getOrCreate(anyString());
		}

	}

}
