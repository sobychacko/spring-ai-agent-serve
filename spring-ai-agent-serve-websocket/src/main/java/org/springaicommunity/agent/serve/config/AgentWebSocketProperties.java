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
 * Configuration properties for the WebSocket transport.
 *
 */
@ConfigurationProperties(prefix = "spring.ai.agent.serve.websocket")
public class AgentWebSocketProperties {

	/**
	 * The WebSocket endpoint path.
	 */
	private String endpoint = "/ws";

	/**
	 * Allowed origin patterns for WebSocket connections (CORS).
	 */
	private String[] allowedOrigins = new String[] { "http://localhost:*" };

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
