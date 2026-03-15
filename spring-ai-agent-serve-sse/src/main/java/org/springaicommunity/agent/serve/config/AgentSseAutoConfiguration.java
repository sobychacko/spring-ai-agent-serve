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

import org.springaicommunity.agent.serve.AgentSessionManager;
import org.springaicommunity.agent.serve.transport.AgentEventSender;
import org.springaicommunity.agent.serve.transport.sse.AgentSseController;
import org.springaicommunity.agent.serve.transport.sse.SseEventSender;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Auto-configuration for SSE transport.
 *
 * <p>
 * Activates in a web application context when {@link SseEmitter} is on the classpath.
 *
 * <p>
 * Each application uses a single transport. This auto-configuration registers
 * {@link SseEventSender} as the {@link AgentEventSender} for the application.
 *
 */
@AutoConfiguration(after = AgentServeAutoConfiguration.class)
@ConditionalOnWebApplication
@ConditionalOnClass(SseEmitter.class)
@EnableConfigurationProperties(AgentSseProperties.class)
public class AgentSseAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	AgentEventSender agentEventSender() {
		return new SseEventSender();
	}

	@Bean
	@ConditionalOnMissingBean
	AgentSseController agentSseController(AgentSessionManager sessionManager,
			AgentEventSender eventSender, AgentSseProperties properties) {
		return new AgentSseController(sessionManager, (SseEventSender) eventSender, properties);
	}

}
