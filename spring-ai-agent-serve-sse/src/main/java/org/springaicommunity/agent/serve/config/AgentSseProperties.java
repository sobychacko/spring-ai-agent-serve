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
 * Configuration properties for the SSE transport.
 *
 */
@ConfigurationProperties(prefix = "spring.ai.agent.serve.sse")
public class AgentSseProperties {

	/**
	 * SseEmitter timeout in milliseconds. Set to 0 for no timeout.
	 */
	private long emitterTimeoutMs = 1_800_000;

	/**
	 * Allowed origin patterns for SSE endpoints (CORS).
	 */
	private String[] allowedOrigins = new String[] { "http://localhost:*" };

	public long getEmitterTimeoutMs() {
		return this.emitterTimeoutMs;
	}

	public void setEmitterTimeoutMs(long emitterTimeoutMs) {
		this.emitterTimeoutMs = emitterTimeoutMs;
	}

	public String[] getAllowedOrigins() {
		return this.allowedOrigins;
	}

	public void setAllowedOrigins(String[] allowedOrigins) {
		this.allowedOrigins = allowedOrigins;
	}

}
