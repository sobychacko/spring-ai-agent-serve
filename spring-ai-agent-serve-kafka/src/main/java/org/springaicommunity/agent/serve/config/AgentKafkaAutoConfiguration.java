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
import org.springaicommunity.agent.serve.transport.kafka.KafkaAgentListener;
import org.springaicommunity.agent.serve.transport.kafka.KafkaEventSender;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.converter.StringJacksonJsonMessageConverter;

/**
 * Auto-configuration for Kafka transport.
 *
 * <p>
 * Activates when {@link KafkaTemplate} is on the classpath. Unlike SSE and WebSocket
 * transports, this does not require a web application context — the Kafka transport
 * is designed for backend agent services in event-driven architectures.
 *
 * <p>
 * Creates a serve-specific {@link ConcurrentKafkaListenerContainerFactory} that reuses
 * Spring Boot's auto-configured {@link ConsumerFactory} but adds a
 * {@link StringJacksonJsonMessageConverter} for type-safe deserialization of
 * {@code AgentRequest} and {@code QuestionAnswer} messages.
 *
 */
@AutoConfiguration(after = AgentServeAutoConfiguration.class)
@ConditionalOnClass(KafkaTemplate.class)
@EnableConfigurationProperties(AgentKafkaProperties.class)
public class AgentKafkaAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(name = "agentServeKafkaListenerContainerFactory")
	ConcurrentKafkaListenerContainerFactory<String, String> agentServeKafkaListenerContainerFactory(
			ConsumerFactory<String, String> consumerFactory, JsonMapper jsonMapper) {
		ConcurrentKafkaListenerContainerFactory<String, String> factory =
				new ConcurrentKafkaListenerContainerFactory<>();
		factory.setConsumerFactory(consumerFactory);
		factory.setRecordMessageConverter(new StringJacksonJsonMessageConverter(jsonMapper));
		return factory;
	}

	@Bean
	@ConditionalOnMissingBean
	AgentEventSender agentEventSender(KafkaTemplate<String, String> kafkaTemplate,
			JsonMapper jsonMapper, AgentKafkaProperties properties) {
		return new KafkaEventSender(kafkaTemplate, jsonMapper, properties.getEventsTopic());
	}

	@Bean
	@ConditionalOnMissingBean
	KafkaAgentListener kafkaAgentListener(AgentSessionManager sessionManager,
			AgentEventSender eventSender) {
		return new KafkaAgentListener(sessionManager, eventSender);
	}

}
