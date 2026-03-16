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

import java.util.List;

import org.springaicommunity.agent.serve.AgentSessionManager;
import org.springaicommunity.agent.serve.InMemoryAgentSessionManager;
import org.springaicommunity.agent.serve.feedback.ServeQuestionHandlerFactory;
import org.springaicommunity.agent.serve.metrics.AgentServeMetrics;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for the Spring AI Agent Serve session management.
 *
 * <p>
 * Activates when a {@link ChatClient} is on the classpath and the serve layer is enabled.
 * Creates the session manager bean. WebSocket transport and question bridge are handled
 * by their own auto-configuration classes.
 *
 * <p>
 * Accepts an optional {@link ServeQuestionHandlerFactory} via {@link ObjectProvider}
 * so that the session manager can wire per-session question handlers when agent-utils is
 * on the classpath.
 *
 * <p>
 * When a {@code List<ToolCallback>} bean is available, those tools are wrapped with
 * observable decorators so the serve layer can emit tool call lifecycle events to clients.
 * To enable this, declare tools as a separate {@code List<ToolCallback>} bean rather than
 * registering them directly on the {@link ChatClient.Builder}.
 *
 */
@AutoConfiguration
@ConditionalOnClass(ChatClient.class)
@ConditionalOnProperty(prefix = "spring.ai.agent.serve", name = "enabled", havingValue = "true",
		matchIfMissing = true)
@EnableConfigurationProperties(AgentServeProperties.class)
public class AgentServeAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	AgentSessionManager agentSessionManager(ChatClient.Builder chatClientBuilder,
			AgentServeProperties properties,
			ObjectProvider<ServeQuestionHandlerFactory> questionHandlerFactory,
			ObjectProvider<List<ToolCallback>> toolCallbacks,
			ObjectProvider<AgentServeMetrics> metrics) {
		return new InMemoryAgentSessionManager(chatClientBuilder, properties.getMaxMessages(),
				questionHandlerFactory.getIfAvailable(), toolCallbacks.getIfAvailable(),
				properties.getSession().getTtl(), properties.getSession().getEvictionInterval(),
				metrics.getIfAvailable());
	}

}
