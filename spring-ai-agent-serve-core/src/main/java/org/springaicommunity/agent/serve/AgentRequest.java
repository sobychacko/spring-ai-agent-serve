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

/**
 * Represents a client request to the agent serve.
 *
 * <p>
 * This record crosses a serialization boundary: the browser sends JSON
 * ({@code {"sessionId":"abc","message":"hello"}}) which Spring's STOMP message converter
 * deserializes into this type automatically. Jackson handles record deserialization
 * natively.
 *
 * <p>
 * The {@code sessionId} may be {@code null} for the first message from a client that
 * doesn't generate its own IDs. The controller will assign a UUID in that case. Records
 * are immutable, so when the controller needs to attach a resolved sessionId, it creates
 * a new {@code AgentRequest} rather than mutating the original.
 *
 * @param sessionId the session identifier for conversation continuity (null for new
 * sessions)
 * @param message the user's message to the agent

 */
public record AgentRequest(String sessionId, String message) {

}
