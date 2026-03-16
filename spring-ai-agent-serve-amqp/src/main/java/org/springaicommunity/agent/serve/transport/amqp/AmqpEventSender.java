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
package org.springaicommunity.agent.serve.transport.amqp;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.agent.serve.AgentEvent;
import org.springaicommunity.agent.serve.transport.AgentEventSender;

import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * AMQP (RabbitMQ) implementation of {@link AgentEventSender}.
 *
 * <p>
 * Serializes {@link AgentEvent} instances to JSON and publishes them to a topic exchange.
 * The {@code sessionId} is used as the routing key, allowing downstream consumers to bind
 * queues with routing key patterns to receive events for specific sessions.
 *
 */
public class AmqpEventSender implements AgentEventSender {

	private static final Logger logger = LoggerFactory.getLogger(AmqpEventSender.class);

	private final RabbitTemplate rabbitTemplate;

	private final ObjectMapper objectMapper;

	private final String eventsExchange;

	public AmqpEventSender(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper,
			String eventsExchange) {
		this.rabbitTemplate = rabbitTemplate;
		this.objectMapper = objectMapper;
		this.eventsExchange = eventsExchange;
	}

	@Override
	public void send(String sessionId, AgentEvent event) {
		try {
			String json = this.objectMapper.writeValueAsString(event);
			this.rabbitTemplate.convertAndSend(this.eventsExchange, sessionId, json);
		}
		catch (JacksonException ex) {
			logger.error("Failed to serialize event [{}] for session [{}]", event.type(), sessionId, ex);
		}
	}

}
