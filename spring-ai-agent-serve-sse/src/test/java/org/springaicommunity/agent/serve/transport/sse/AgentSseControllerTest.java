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
import org.springaicommunity.agent.serve.config.AgentSseProperties;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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
 * Tests for {@link AgentSseController}.
 */
@DisplayName("AgentSseController Tests")
@ExtendWith(MockitoExtension.class)
class AgentSseControllerTest {

	@Mock
	private AgentSessionManager sessionManager;

	@Mock
	private SseEventSender eventSender;

	@Mock
	private AgentSession session;

	private AgentSseController controller;

	@BeforeEach
	void setUp() {
		AgentSseProperties properties = new AgentSseProperties();
		this.controller = new AgentSseController(this.sessionManager, this.eventSender, properties);
	}

	@Nested
	@DisplayName("SSE Connection")
	class SseConnection {

		@Test
		@DisplayName("Should return SseEmitter and register with sender")
		void shouldReturnEmitterAndRegister() {
			when(sessionManager.getOrCreate("s1")).thenReturn(session);

			SseEmitter emitter = controller.connect("s1");

			assertThat(emitter).isNotNull();
			verify(eventSender).register(eq("s1"), eq(emitter));
			verify(sessionManager).getOrCreate("s1");
		}

		@Test
		@DisplayName("Should generate UUID when sessionId is 'new'")
		void shouldGenerateUuidForNewSession() {
			when(sessionManager.getOrCreate(anyString())).thenReturn(session);

			controller.connect("new");

			ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
			verify(sessionManager).getOrCreate(captor.capture());
			assertThatNoException().isThrownBy(() -> UUID.fromString(captor.getValue()));
		}

		@Test
		@DisplayName("Should send SESSION_CREATED event for new sessions")
		void shouldSendSessionCreatedForNewSession() {
			when(sessionManager.getOrCreate(anyString())).thenReturn(session);

			controller.connect("new");

			ArgumentCaptor<AgentEvent> eventCaptor = ArgumentCaptor.forClass(AgentEvent.class);
			verify(eventSender).send(anyString(), eventCaptor.capture());
			assertThat(eventCaptor.getValue().type()).isEqualTo("SESSION_CREATED");
		}

	}

	@Nested
	@DisplayName("Send Message")
	class SendMessage {

		@Test
		@DisplayName("Should submit stream and return 202")
		@SuppressWarnings("unchecked")
		void shouldSubmitStreamAndReturn202() {
			when(sessionManager.getOrCreate("s1")).thenReturn(session);

			ResponseEntity<Map<String, String>> response =
					controller.sendMessage("s1", new AgentRequest("s1", "hello"));

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
			assertThat(response.getBody()).containsEntry("sessionId", "s1");
			verify(session).submitStream(any(AgentRequest.class), any(Consumer.class));
		}

		@Test
		@DisplayName("Should send streaming events via SSE")
		@SuppressWarnings("unchecked")
		void shouldSendStreamingEvents() {
			AgentEvent chunk = AgentEvent.responseChunk("s1", "Hello");
			when(sessionManager.getOrCreate("s1")).thenReturn(session);

			doAnswer(invocation -> {
				Consumer<AgentEvent> listener = invocation.getArgument(1);
				listener.accept(chunk);
				return null;
			}).when(session).submitStream(any(AgentRequest.class), any(Consumer.class));

			controller.sendMessage("s1", new AgentRequest("s1", "Hi"));

			verify(eventSender).send(eq("s1"), eq(chunk));
		}

	}

	@Nested
	@DisplayName("Answer Routing")
	class AnswerRouting {

		@Test
		@DisplayName("Should route answer to session and return 204")
		void shouldRouteAnswerToSession() {
			when(sessionManager.get("s1")).thenReturn(Optional.of(session));
			Map<String, String> answers = Map.of("Which?", "Option A");

			ResponseEntity<Void> response =
					controller.sendAnswer("s1", new QuestionAnswer("s1", "q-123", answers));

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
			verify(session).resolveQuestion("q-123", answers);
		}

		@Test
		@DisplayName("Should return 404 for unknown session")
		void shouldReturn404ForUnknownSession() {
			when(sessionManager.get("unknown")).thenReturn(Optional.empty());

			ResponseEntity<Void> response =
					controller.sendAnswer("unknown", new QuestionAnswer("unknown", "q-123", Map.of()));

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
			verify(sessionManager, never()).getOrCreate(anyString());
		}

	}

}
