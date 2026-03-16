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
package org.springaicommunity.agent.serve.config;

import tools.jackson.databind.json.JsonMapper;

import org.springaicommunity.agent.serve.AgentSessionManager;
import org.springaicommunity.agent.serve.transport.AgentEventSender;
import org.springaicommunity.agent.serve.transport.amqp.AmqpAgentListener;
import org.springaicommunity.agent.serve.transport.amqp.AmqpEventSender;

import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for AMQP (RabbitMQ) transport.
 *
 * <p>
 * Activates when {@link RabbitTemplate} is on the classpath. Unlike SSE and WebSocket
 * transports, this does not require a web application context — the AMQP transport
 * is designed for backend agent services in event-driven architectures.
 *
 * <p>
 * Creates a serve-specific {@link SimpleRabbitListenerContainerFactory} that reuses
 * Spring Boot's auto-configured {@link ConnectionFactory} but adds a
 * {@link JacksonJsonMessageConverter} for type-safe deserialization of
 * {@code AgentRequest} and {@code QuestionAnswer} messages.
 *
 * <p>
 * Declares the AMQP topology (queues and exchange) via a {@link Declarables} bean
 * so that RabbitMQ creates these resources automatically on startup.
 *
 */
@AutoConfiguration(after = AgentServeAutoConfiguration.class)
@ConditionalOnClass(RabbitTemplate.class)
@EnableConfigurationProperties(AgentAmqpProperties.class)
public class AgentAmqpAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(name = "agentServeRabbitListenerContainerFactory")
	SimpleRabbitListenerContainerFactory agentServeRabbitListenerContainerFactory(
			ConnectionFactory connectionFactory, JsonMapper jsonMapper) {
		SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
		factory.setConnectionFactory(connectionFactory);
		factory.setMessageConverter(new JacksonJsonMessageConverter(jsonMapper));
		return factory;
	}

	@Bean
	@ConditionalOnMissingBean
	AgentEventSender agentEventSender(RabbitTemplate rabbitTemplate,
			JsonMapper jsonMapper, AgentAmqpProperties properties) {
		return new AmqpEventSender(rabbitTemplate, jsonMapper, properties.getEventsExchange());
	}

	@Bean
	@ConditionalOnMissingBean
	AmqpAgentListener amqpAgentListener(AgentSessionManager sessionManager,
			AgentEventSender eventSender) {
		return new AmqpAgentListener(sessionManager, eventSender);
	}

	@Bean
	@ConditionalOnMissingBean(name = "agentServeAmqpDeclarables")
	Declarables agentServeAmqpDeclarables(AgentAmqpProperties properties) {
		return new Declarables(
				new Queue(properties.getRequestQueue(), true),
				new Queue(properties.getAnswerQueue(), true),
				new TopicExchange(properties.getEventsExchange()));
	}

}
