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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link ObservableToolCallback}.
 */
@DisplayName("ObservableToolCallback Tests")
@ExtendWith(MockitoExtension.class)
class ObservableToolCallbackTest {

	@Mock
	private ToolCallback delegate;

	@Mock
	private ToolDefinition toolDefinition;

	@Mock
	private ToolMetadata toolMetadata;

	@Mock
	private ToolContext toolContext;

	private ToolCallEventBridge bridge;

	private List<AgentEvent> events;

	private ObservableToolCallback observable;

	@BeforeEach
	void setUp() {
		this.bridge = new ToolCallEventBridge("test-session");
		this.events = new ArrayList<>();
		this.bridge.setEventCallback(this.events::add);
		this.observable = new ObservableToolCallback(this.delegate, this.bridge);
	}

	private void wireToolName(String name) {
		when(this.delegate.getToolDefinition()).thenReturn(this.toolDefinition);
		when(this.toolDefinition.name()).thenReturn(name);
	}

	@Test
	@DisplayName("Should emit TOOL_CALL_STARTED and TOOL_CALL_COMPLETED around call(String)")
	void shouldEmitEventsAroundCall() {
		wireToolName("GrepTool");
		when(this.delegate.call("{\"pattern\":\"foo\"}")).thenReturn("result");

		String result = this.observable.call("{\"pattern\":\"foo\"}");

		assertThat(result).isEqualTo("result");
		assertThat(this.events).hasSize(2);
		assertThat(this.events.get(0).type()).isEqualTo("TOOL_CALL_STARTED");
		assertThat(this.events.get(0).metadata()).containsEntry("toolName", "GrepTool");
		assertThat(this.events.get(1).type()).isEqualTo("TOOL_CALL_COMPLETED");
	}

	@Test
	@DisplayName("Should emit TOOL_CALL_STARTED and TOOL_CALL_COMPLETED around call(String, ToolContext)")
	void shouldEmitEventsAroundCallWithContext() {
		wireToolName("GrepTool");
		when(this.delegate.call("{}", this.toolContext)).thenReturn("ok");

		String result = this.observable.call("{}", this.toolContext);

		assertThat(result).isEqualTo("ok");
		assertThat(this.events).hasSize(2);
		assertThat(this.events.get(0).type()).isEqualTo("TOOL_CALL_STARTED");
		assertThat(this.events.get(1).type()).isEqualTo("TOOL_CALL_COMPLETED");
	}

	@Test
	@DisplayName("Should emit TOOL_CALL_COMPLETED even when tool throws")
	void shouldEmitCompletedOnException() {
		wireToolName("GrepTool");
		when(this.delegate.call("bad")).thenThrow(new RuntimeException("tool failed"));

		assertThatThrownBy(() -> this.observable.call("bad")).hasMessage("tool failed");

		assertThat(this.events).hasSize(2);
		assertThat(this.events.get(0).type()).isEqualTo("TOOL_CALL_STARTED");
		assertThat(this.events.get(1).type()).isEqualTo("TOOL_CALL_COMPLETED");
	}

	@Test
	@DisplayName("Should delegate getToolDefinition")
	void shouldDelegateToolDefinition() {
		when(this.delegate.getToolDefinition()).thenReturn(this.toolDefinition);
		assertThat(this.observable.getToolDefinition()).isSameAs(this.toolDefinition);
	}

	@Test
	@DisplayName("Should delegate getToolMetadata")
	void shouldDelegateToolMetadata() {
		when(this.delegate.getToolMetadata()).thenReturn(this.toolMetadata);
		assertThat(this.observable.getToolMetadata()).isSameAs(this.toolMetadata);
	}

}
