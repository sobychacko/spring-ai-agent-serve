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
package org.springaicommunity.agent.serve.programmatic;

import java.util.Arrays;
import java.util.List;

import org.springaicommunity.agent.tools.FileSystemTools;
import org.springaicommunity.agent.tools.GlobTool;
import org.springaicommunity.agent.tools.GrepTool;
import org.springaicommunity.agent.tools.ShellTools;
import org.springaicommunity.agent.tools.TodoWriteTool;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Programmatic demo that uses the serve layer's core library directly without any transport
 * layer.
 *
 * <p>
 * This application demonstrates the serve layer's value as a standalone library: session
 * management, streaming, tool call events, question bridge, and multi-turn memory -- all
 * driven by a simple {@code Consumer<AgentEvent>} callback with no WebSocket or messaging
 * infrastructure.
 *
 * <p>
 * Since this application only depends on {@code spring-ai-agent-serve} (not the
 * starter), WebSocket/messaging classes are not on the classpath. The
 * {@code AgentWebSocketAutoConfiguration} won't activate because its
 * {@code @ConditionalOnClass(SimpMessagingTemplate.class)} condition is not met. The core
 * {@code AgentServeAutoConfiguration} still creates the {@code InMemoryAgentSessionManager}
 * from the application's {@code ChatClient.Builder} bean.
 */
@SpringBootApplication
public class Application {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	@Bean
	ChatClient.Builder agentChatClientBuilder(ChatModel chatModel) {
		return ChatClient.builder(chatModel)
			.defaultSystem("You are a helpful AI assistant. You can help with coding tasks, "
					+ "file operations, and general questions. Be concise and helpful. "
					+ "When a request is ambiguous, use the AskUserQuestionTool to ask "
					+ "clarifying questions before proceeding. Always present clear options "
					+ "to the user when there are multiple valid approaches.");
	}

	@Bean
	List<ToolCallback> agentTools() {
		return Arrays.asList(ToolCallbacks.from(GrepTool.builder().build(), GlobTool.builder().build(),
				FileSystemTools.builder().build(), ShellTools.builder().build(), TodoWriteTool.builder().build()));
	}

}
