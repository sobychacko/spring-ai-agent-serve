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
package org.springaicommunity.agent.serve.health;

import org.springaicommunity.agent.serve.AgentSessionManager;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * Actuator health indicator for the Agent Serve layer.
 *
 * <p>
 * Reports the number of active sessions. Available at {@code /actuator/health} when
 * Spring Boot Actuator is on the classpath.
 *
 */
public class AgentServeHealthIndicator implements HealthIndicator {

	private final AgentSessionManager sessionManager;

	public AgentServeHealthIndicator(AgentSessionManager sessionManager) {
		this.sessionManager = sessionManager;
	}

	@Override
	public Health health() {
		return Health.up()
			.withDetail("activeSessions", this.sessionManager.activeSessions().size())
			.build();
	}

}
