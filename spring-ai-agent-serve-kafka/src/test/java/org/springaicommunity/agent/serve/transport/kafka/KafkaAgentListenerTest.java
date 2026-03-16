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

import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springaicommunity.agent.serve.AgentEvent;
import org.springaicommunity.agent.serve.AgentRequest;
import org.springaicommunity.agent.serve.AgentSession;
import org.springaicommunity.agent.serve.AgentSessionManager;
import org.springaicommunity.agent.serve.QuestionAnswer;
import org.springaicommunity.agent.serve.transport.AgentEventSender;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link KafkaAgentListener}.
 */
@DisplayName("KafkaAgentListener Tests")
@ExtendWith(MockitoExtension.class)
class KafkaAgentListenerTest {

	@Mock
	private AgentSessionManager sessionManager;

	@Mock
	private AgentEventSender eventSender;

	@Mock
	private AgentSession session;

	private KafkaAgentListener listener;

	@BeforeEach
	void setUp() {
		this.listener = new KafkaAgentListener(this.sessionManager, this.eventSender);
	}

	@Nested
	@DisplayName("Request Handling")
	class RequestHandling {

		@Test
		@DisplayName("Should create session and submit stream on request")
		@SuppressWarnings("unchecked")
		void shouldCreateSessionAndSubmitStream() {
			when(sessionManager.getOrCreate("s1")).thenReturn(session);

			listener.onRequest(new AgentRequest("s1", "hello"));

			verify(sessionManager).getOrCreate("s1");
			verify(session).submitStream(any(AgentRequest.class), any(Consumer.class));
		}

		@Test
		@DisplayName("Should route events to event sender during stream")
		@SuppressWarnings("unchecked")
		void shouldRouteEventsToEventSender() {
			AgentEvent chunk = AgentEvent.responseChunk("s1", "Hello");
			when(sessionManager.getOrCreate("s1")).thenReturn(session);

			doAnswer(invocation -> {
				Consumer<AgentEvent> callback = invocation.getArgument(1);
				callback.accept(chunk);
				return null;
			}).when(session).submitStream(any(AgentRequest.class), any(Consumer.class));

			listener.onRequest(new AgentRequest("s1", "Hi"));

			verify(eventSender).send(eq("s1"), eq(chunk));
		}

	}

	@Nested
	@DisplayName("Answer Handling")
	class AnswerHandling {

		@Test
		@DisplayName("Should resolve question on answer")
		void shouldResolveQuestion() {
			when(sessionManager.get("s1")).thenReturn(Optional.of(session));
			Map<String, String> answers = Map.of("Which?", "Option A");

			listener.onAnswer(new QuestionAnswer("s1", "q-123", answers));

			verify(session).resolveQuestion("q-123", answers);
		}

		@Test
		@DisplayName("Should ignore answer for unknown session")
		void shouldIgnoreUnknownSession() {
			when(sessionManager.get("unknown")).thenReturn(Optional.empty());

			listener.onAnswer(new QuestionAnswer("unknown", "q-123", Map.of()));

			verify(sessionManager, never()).getOrCreate(anyString());
		}

	}

}
