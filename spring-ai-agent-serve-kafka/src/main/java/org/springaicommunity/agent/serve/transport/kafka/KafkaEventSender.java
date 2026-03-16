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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.agent.serve.AgentEvent;
import org.springaicommunity.agent.serve.transport.AgentEventSender;

import org.springframework.kafka.core.KafkaTemplate;

/**
 * Kafka implementation of {@link AgentEventSender}.
 *
 * <p>
 * Serializes {@link AgentEvent} instances to JSON and publishes them to a Kafka topic.
 * The {@code sessionId} is used as the Kafka message key, which provides partition
 * ordering — all events for the same session are delivered to the same partition in order.
 *
 */
public class KafkaEventSender implements AgentEventSender {

	private static final Logger logger = LoggerFactory.getLogger(KafkaEventSender.class);

	private final KafkaTemplate<String, String> kafkaTemplate;

	private final ObjectMapper objectMapper;

	private final String eventsTopic;

	public KafkaEventSender(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper,
			String eventsTopic) {
		this.kafkaTemplate = kafkaTemplate;
		this.objectMapper = objectMapper;
		this.eventsTopic = eventsTopic;
	}

	@Override
	public void send(String sessionId, AgentEvent event) {
		try {
			String json = this.objectMapper.writeValueAsString(event);
			this.kafkaTemplate.send(this.eventsTopic, sessionId, json);
		}
		catch (JacksonException ex) {
			logger.error("Failed to serialize event [{}] for session [{}]", event.type(), sessionId, ex);
		}
	}

}
