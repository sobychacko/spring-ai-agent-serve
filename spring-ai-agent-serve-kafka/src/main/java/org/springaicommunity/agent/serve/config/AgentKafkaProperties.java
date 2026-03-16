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

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Kafka transport.
 *
 */
@ConfigurationProperties(prefix = "spring.ai.agent.serve.kafka")
public class AgentKafkaProperties {

	/**
	 * Inbound topic for user messages ({@code AgentRequest}).
	 */
	private String requestsTopic = "agent-requests";

	/**
	 * Inbound topic for question answers ({@code QuestionAnswer}).
	 */
	private String answersTopic = "agent-answers";

	/**
	 * Outbound topic for agent events ({@code AgentEvent}).
	 */
	private String eventsTopic = "agent-events";

	/**
	 * Consumer group for the serve layer's Kafka listeners.
	 */
	private String consumerGroup = "agent-serve";

	public String getRequestsTopic() {
		return this.requestsTopic;
	}

	public void setRequestsTopic(String requestsTopic) {
		this.requestsTopic = requestsTopic;
	}

	public String getAnswersTopic() {
		return this.answersTopic;
	}

	public void setAnswersTopic(String answersTopic) {
		this.answersTopic = answersTopic;
	}

	public String getEventsTopic() {
		return this.eventsTopic;
	}

	public void setEventsTopic(String eventsTopic) {
		this.eventsTopic = eventsTopic;
	}

	public String getConsumerGroup() {
		return this.consumerGroup;
	}

	public void setConsumerGroup(String consumerGroup) {
		this.consumerGroup = consumerGroup;
	}

}
