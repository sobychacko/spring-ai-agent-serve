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

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springaicommunity.agent.serve.AgentEvent;

import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link KafkaEventSender}.
 */
@DisplayName("KafkaEventSender Tests")
@ExtendWith(MockitoExtension.class)
class KafkaEventSenderTest {

	@Mock
	private KafkaTemplate<String, String> kafkaTemplate;

	private ObjectMapper objectMapper;

	private KafkaEventSender sender;

	@BeforeEach
	void setUp() {
		this.objectMapper = new ObjectMapper();
		this.sender = new KafkaEventSender(this.kafkaTemplate, this.objectMapper, "agent-events");
	}

	@Test
	@DisplayName("Should send event as JSON with sessionId as key")
	void shouldSendEventAsJsonWithSessionIdAsKey() throws Exception {
		AgentEvent event = AgentEvent.responseChunk("s1", "Hello");
		String expectedJson = this.objectMapper.writeValueAsString(event);

		this.sender.send("s1", event);

		verify(this.kafkaTemplate).send("agent-events", "s1", expectedJson);
	}

	@Test
	@DisplayName("Should use configured topic name")
	void shouldUseConfiguredTopicName() throws Exception {
		KafkaEventSender customSender = new KafkaEventSender(this.kafkaTemplate, this.objectMapper,
				"custom-events");
		AgentEvent event = AgentEvent.finalResponse("s1", "Done");

		customSender.send("s1", event);

		verify(this.kafkaTemplate).send(eq("custom-events"), eq("s1"), anyString());
	}

	@Test
	@DisplayName("Should handle serialization error gracefully")
	void shouldHandleSerializationErrorGracefully() throws Exception {
		ObjectMapper failingMapper = org.mockito.Mockito.mock(ObjectMapper.class);
		when(failingMapper.writeValueAsString(org.mockito.ArgumentMatchers.any()))
			.thenThrow(new JacksonException("test") {});
		KafkaEventSender failingSender = new KafkaEventSender(this.kafkaTemplate, failingMapper,
				"agent-events");
		AgentEvent event = AgentEvent.responseChunk("s1", "Hello");

		assertThatNoException().isThrownBy(() -> failingSender.send("s1", event));
		verify(this.kafkaTemplate, never()).send(anyString(), anyString(), anyString());
	}

}
