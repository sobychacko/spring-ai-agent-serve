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

import java.util.function.Consumer;

/**
 * Per-session bridge that emits tool call lifecycle events to the active listener.
 *
 * <p>
 * Created once per session and shared with all {@link ObservableToolCallback} wrappers
 * for that session. Before each streaming request, the {@link AgentSession} sets the
 * current event callback via {@link #setEventCallback(Consumer)}. Tool callbacks then
 * emit {@code TOOL_CALL_STARTED} and {@code TOOL_CALL_COMPLETED} events through this
 * bridge.
 *
 * <p>
 * The callback reference is {@code volatile} to ensure visibility between the session's
 * executor thread (which sets the callback) and the {@code boundedElastic} scheduler
 * thread where Spring AI executes tool calls during streaming.
 *
 */
public class ToolCallEventBridge {

	private final String sessionId;

	private volatile Consumer<AgentEvent> callback;

	public ToolCallEventBridge(String sessionId) {
		this.sessionId = sessionId;
	}

	public void setEventCallback(Consumer<AgentEvent> callback) {
		this.callback = callback;
	}

	void toolCallStarted(String toolName) {
		Consumer<AgentEvent> cb = this.callback;
		if (cb != null) {
			cb.accept(AgentEvent.toolCallStarted(this.sessionId, toolName));
		}
	}

	void toolCallCompleted(String toolName) {
		Consumer<AgentEvent> cb = this.callback;
		if (cb != null) {
			cb.accept(AgentEvent.toolCallCompleted(this.sessionId, toolName));
		}
	}

}
