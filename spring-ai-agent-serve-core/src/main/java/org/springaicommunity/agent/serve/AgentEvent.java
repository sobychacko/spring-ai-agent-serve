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

import java.util.Map;

/**
 * Represents an event from the agent serve to the client.
 *
 * <p>
 * Replaces the simpler {@code AgentResponse} from iteration 1 with a richer event model
 * that supports streaming chunks, tool call lifecycle events, and question/answer bridging.
 *
 * <p>
 * The {@code type} field is a String rather than an enum because this record crosses a
 * serialization boundary — the browser reads {@code event.type === 'RESPONSE_CHUNK'} as a
 * string comparison.
 *
 * <p>
 * The {@code metadata} map carries event-specific payloads (tool names, question data)
 * without polluting the core record structure with optional fields. For example:
 * <ul>
 *   <li>TOOL_CALL_STARTED: metadata may contain {@code "toolName"}</li>
 *   <li>QUESTION_REQUIRED: metadata contains {@code "questionId"} and {@code "questions"}</li>
 * </ul>
 *
 * <p>
 * Static factory methods centralize the type string literals so callers never write raw
 * type strings.
 *
 * @param sessionId the session identifier
 * @param type the event type
 * @param content the text content (may be null for non-text events)
 * @param metadata event-specific key-value pairs
 */
public record AgentEvent(String sessionId, String type, String content, Map<String, Object> metadata) {

	public static AgentEvent responseChunk(String sessionId, String content) {
		return new AgentEvent(sessionId, "RESPONSE_CHUNK", content, Map.of());
	}

	public static AgentEvent toolCallStarted(String sessionId, String toolName) {
		return new AgentEvent(sessionId, "TOOL_CALL_STARTED", null,
				toolName != null ? Map.of("toolName", toolName) : Map.of());
	}

	public static AgentEvent toolCallCompleted(String sessionId, String toolName) {
		return new AgentEvent(sessionId, "TOOL_CALL_COMPLETED", null,
				toolName != null ? Map.of("toolName", toolName) : Map.of());
	}

	public static AgentEvent questionRequired(String sessionId, String questionId, Object questions) {
		return new AgentEvent(sessionId, "QUESTION_REQUIRED", null,
				Map.of("questionId", questionId, "questions", questions));
	}

	public static AgentEvent finalResponse(String sessionId, String content) {
		return new AgentEvent(sessionId, "FINAL_RESPONSE", content, Map.of());
	}

	public static AgentEvent error(String sessionId, String errorMessage) {
		return new AgentEvent(sessionId, "ERROR", errorMessage, Map.of());
	}

}
