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

import org.springaicommunity.agent.serve.feedback.ServeQuestionHandlerFactory;
import org.springaicommunity.agent.tools.AskUserQuestionTool;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for the AskUserQuestionTool bridge.
 *
 * <p>
 * Activates when {@link AskUserQuestionTool} is on the classpath (i.e., when the
 * application depends on spring-ai-agent-utils). Creates a
 * {@link ServeQuestionHandlerFactory} that the session manager uses to wire per-session
 * question handlers.
 *
 */
@AutoConfiguration(before = AgentServeAutoConfiguration.class)
@ConditionalOnClass(AskUserQuestionTool.class)
@EnableConfigurationProperties(AgentServeProperties.class)
public class AgentQuestionBridgeAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	ServeQuestionHandlerFactory serveQuestionHandlerFactory(AgentServeProperties properties) {
		return new ServeQuestionHandlerFactory(properties.getQuestion().getTimeoutMinutes());
	}

}
