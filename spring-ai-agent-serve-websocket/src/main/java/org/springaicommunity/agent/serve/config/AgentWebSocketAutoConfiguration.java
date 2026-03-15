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
import org.springaicommunity.agent.serve.transport.websocket.AgentWebSocketConfig;
import org.springaicommunity.agent.serve.transport.websocket.AgentWebSocketController;
import org.springaicommunity.agent.serve.transport.websocket.WebSocketEventSender;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Auto-configuration for WebSocket transport.
 *
 * <p>
 * Activates only in a web application context when both {@link SimpMessagingTemplate} and
 * {@link WebSocketMessageBrokerConfigurer} are on the classpath. The web application
 * condition prevents this server-side configuration from activating in CLI or standalone
 * applications that may have WebSocket client libraries on the classpath.
 *
 * <p>
 * Each application uses a single transport. This auto-configuration registers
 * {@link WebSocketEventSender} as the {@link AgentEventSender} for the application.
 *
 */
@AutoConfiguration(after = AgentServeAutoConfiguration.class)
@ConditionalOnWebApplication
@ConditionalOnClass({ SimpMessagingTemplate.class, WebSocketMessageBrokerConfigurer.class })
@EnableConfigurationProperties(AgentWebSocketProperties.class)
@EnableWebSocketMessageBroker
public class AgentWebSocketAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	AgentEventSender agentEventSender(SimpMessagingTemplate messagingTemplate) {
		return new WebSocketEventSender(messagingTemplate);
	}

	@Bean
	@ConditionalOnMissingBean
	AgentWebSocketConfig agentWebSocketConfig(AgentWebSocketProperties properties) {
		return new AgentWebSocketConfig(properties);
	}

	@Bean
	@ConditionalOnMissingBean
	AgentWebSocketController agentWebSocketController(AgentSessionManager sessionManager,
			AgentEventSender eventSender) {
		return new AgentWebSocketController(sessionManager, eventSender);
	}

}
