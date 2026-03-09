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
 * Configuration properties for the agent serve.
 *
 */
@ConfigurationProperties(prefix = "spring.ai.agent.serve")
public class AgentServeProperties {

	/**
	 * Whether the serve layer is enabled.
	 */
	private boolean enabled = true;

	/**
	 * Maximum number of messages to keep in session memory.
	 */
	private int maxMessages = 500;

	/**
	 * WebSocket configuration.
	 */
	private Websocket websocket = new Websocket();

	/**
	 * Question bridge configuration.
	 */
	private Question question = new Question();

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public int getMaxMessages() {
		return this.maxMessages;
	}

	public void setMaxMessages(int maxMessages) {
		this.maxMessages = maxMessages;
	}

	public Websocket getWebsocket() {
		return this.websocket;
	}

	public void setWebsocket(Websocket websocket) {
		this.websocket = websocket;
	}

	public Question getQuestion() {
		return this.question;
	}

	public void setQuestion(Question question) {
		this.question = question;
	}

	public static class Websocket {

		/**
		 * Whether WebSocket transport is enabled.
		 */
		private boolean enabled = true;

		/**
		 * The WebSocket endpoint path.
		 */
		private String endpoint = "/ws";

		/**
		 * Allowed origin patterns for WebSocket connections (CORS).
		 */
		private String[] allowedOrigins = new String[] { "http://localhost:*" };

		public boolean isEnabled() {
			return this.enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public String getEndpoint() {
			return this.endpoint;
		}

		public void setEndpoint(String endpoint) {
			this.endpoint = endpoint;
		}

		public String[] getAllowedOrigins() {
			return this.allowedOrigins;
		}

		public void setAllowedOrigins(String[] allowedOrigins) {
			this.allowedOrigins = allowedOrigins;
		}

	}

	public static class Question {

		/**
		 * Timeout in minutes for waiting for a user answer to a question.
		 */
		private long timeoutMinutes = 5;

		public long getTimeoutMinutes() {
			return this.timeoutMinutes;
		}

		public void setTimeoutMinutes(long timeoutMinutes) {
			this.timeoutMinutes = timeoutMinutes;
		}

	}

}
