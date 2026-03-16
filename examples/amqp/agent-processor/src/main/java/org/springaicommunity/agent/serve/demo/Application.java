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
package org.springaicommunity.agent.serve.demo;

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
 * AMQP-backed agent processor.
 *
 * <p>
 * This application consumes {@code AgentRequest} messages from a RabbitMQ queue,
 * processes them through a Spring AI agent, and produces {@code AgentEvent} messages
 * to a topic exchange. No web server is started — this is a pure consumer/producer
 * backend service.
 *
 * <p>
 * The AMQP starter auto-configures the listeners, event sender, and session management.
 * This class only defines the agent's capabilities: a system prompt and tools.
 *
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
		return Arrays.asList(ToolCallbacks.from(
				GrepTool.builder().build(), GlobTool.builder().build(),
				FileSystemTools.builder().build(), ShellTools.builder().build(),
				TodoWriteTool.builder().build()));
	}

}
