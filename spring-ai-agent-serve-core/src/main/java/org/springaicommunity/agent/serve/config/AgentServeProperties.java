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

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the agent serve core.
 *
 * <p>
 * Transport-specific properties (WebSocket, SSE) are owned by their respective modules.
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
	 * Session lifecycle configuration.
	 */
	private Session session = new Session();

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

	public Session getSession() {
		return this.session;
	}

	public void setSession(Session session) {
		this.session = session;
	}

	public Question getQuestion() {
		return this.question;
	}

	public void setQuestion(Question question) {
		this.question = question;
	}

	public static class Session {

		/**
		 * Idle session time-to-live. Sessions with no activity for this duration will be
		 * evicted. Set to zero to disable eviction.
		 */
		private Duration ttl = Duration.ofMinutes(30);

		/**
		 * How often the eviction task runs to check for idle sessions.
		 */
		private Duration evictionInterval = Duration.ofSeconds(60);

		public Duration getTtl() {
			return this.ttl;
		}

		public void setTtl(Duration ttl) {
			this.ttl = ttl;
		}

		public Duration getEvictionInterval() {
			return this.evictionInterval;
		}

		public void setEvictionInterval(Duration evictionInterval) {
			this.evictionInterval = evictionInterval;
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
