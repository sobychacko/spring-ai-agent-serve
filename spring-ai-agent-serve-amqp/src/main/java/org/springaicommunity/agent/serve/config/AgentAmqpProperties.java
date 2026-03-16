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
 * Configuration properties for the AMQP (RabbitMQ) transport.
 *
 */
@ConfigurationProperties(prefix = "spring.ai.agent.serve.amqp")
public class AgentAmqpProperties {

	/**
	 * Inbound queue for user messages ({@code AgentRequest}).
	 */
	private String requestQueue = "agent-requests";

	/**
	 * Inbound queue for question answers ({@code QuestionAnswer}).
	 */
	private String answerQueue = "agent-answers";

	/**
	 * Outbound topic exchange for agent events ({@code AgentEvent}).
	 */
	private String eventsExchange = "agent-events";

	public String getRequestQueue() {
		return this.requestQueue;
	}

	public void setRequestQueue(String requestQueue) {
		this.requestQueue = requestQueue;
	}

	public String getAnswerQueue() {
		return this.answerQueue;
	}

	public void setAnswerQueue(String answerQueue) {
		this.answerQueue = answerQueue;
	}

	public String getEventsExchange() {
		return this.eventsExchange;
	}

	public void setEventsExchange(String eventsExchange) {
		this.eventsExchange = eventsExchange;
	}

}
