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

import io.micrometer.core.instrument.MeterRegistry;

import org.springaicommunity.agent.serve.AgentSessionManager;
import org.springaicommunity.agent.serve.health.AgentServeHealthIndicator;
import org.springaicommunity.agent.serve.metrics.AgentServeMetrics;

import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for Agent Serve observability.
 *
 * <p>
 * Registers Micrometer metrics when {@link MeterRegistry} is on the classpath,
 * and an Actuator health indicator when Spring Boot Actuator is available.
 *
 */
@AutoConfiguration(before = AgentServeAutoConfiguration.class)
@ConditionalOnClass(MeterRegistry.class)
public class AgentServeObservabilityAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	AgentServeMetrics agentServeMetrics(MeterRegistry registry) {
		return new AgentServeMetrics(registry);
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnClass(HealthIndicator.class)
	AgentServeHealthIndicator agentServeHealthIndicator(AgentSessionManager sessionManager) {
		return new AgentServeHealthIndicator(sessionManager);
	}

}
