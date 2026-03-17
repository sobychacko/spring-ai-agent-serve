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
package org.springaicommunity.agent.serve;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

/**
 * Decorator that wraps a {@link ToolCallback} to emit tool call lifecycle events via a
 * {@link ToolCallEventBridge}.
 *
 * <p>
 * When a tool is invoked, this wrapper emits a {@code TOOL_CALL_STARTED} event before
 * delegating to the real tool, and a {@code TOOL_CALL_COMPLETED} event after the tool
 * returns (or throws). This allows the serve layer to show real-time tool call status
 * indicators in the UI.
 *
 * <p>
 * This approach intercepts tool calls at the execution layer rather than trying to detect
 * them from stream chunks. Spring AI's {@code AnthropicChatModel.internalStream()} handles
 * tool calls internally via {@code flatMap}, so tool call chunks never reach the
 * subscriber's {@code doOnNext} handler. Wrapping the tool callbacks themselves is the
 * reliable way to detect tool execution.
 *
 */
public class ObservableToolCallback implements ToolCallback {

	private final ToolCallback delegate;

	private final ToolCallEventBridge bridge;

	public ObservableToolCallback(ToolCallback delegate, ToolCallEventBridge bridge) {
		this.delegate = delegate;
		this.bridge = bridge;
	}

	@Override
	public ToolDefinition getToolDefinition() {
		return this.delegate.getToolDefinition();
	}

	@Override
	public ToolMetadata getToolMetadata() {
		return this.delegate.getToolMetadata();
	}

	@Override
	public String call(String toolInput) {
		String toolName = getToolDefinition().name();
		this.bridge.toolCallStarted(toolName);
		try {
			return this.delegate.call(toolInput);
		}
		finally {
			this.bridge.toolCallCompleted(toolName);
		}
	}

	@Override
	public String call(String toolInput, ToolContext toolContext) {
		String toolName = getToolDefinition().name();
		this.bridge.toolCallStarted(toolName);
		try {
			return this.delegate.call(toolInput, toolContext);
		}
		finally {
			this.bridge.toolCallCompleted(toolName);
		}
	}

}
