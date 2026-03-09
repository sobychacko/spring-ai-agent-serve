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

import java.util.Collection;
import java.util.Optional;

/**
 * Manages agent session lifecycle -- creation, retrieval, and destruction.
 *
 * <p>
 * This is the primary extension point for production deployments. The serve layer ships
 * with {@link InMemoryAgentSessionManager} (ConcurrentHashMap-based), but production
 * teams replace it with Redis, JDBC, or Hazelcast-backed implementations. The auto-config
 * uses {@code @ConditionalOnMissingBean}, so defining your own bean replaces the default.
 *

 */
public interface AgentSessionManager {

	/**
	 * Returns the existing session for the given ID, if one exists.
	 * @param sessionId the session identifier
	 * @return an Optional containing the session, or empty if no session exists
	 */
	Optional<AgentSession> get(String sessionId);

	/**
	 * Returns the session for the given ID, creating a new one if it doesn't exist.
	 * <p>
	 * New sessions are created by cloning the application's {@code ChatClient.Builder}
	 * and binding a fresh memory advisor with the session ID as conversation ID. This
	 * ensures each session has isolated memory and advisor state.
	 * @param sessionId the session identifier
	 * @return the agent session
	 */
	AgentSession getOrCreate(String sessionId);

	/**
	 * Destroys the session with the given ID, releasing its resources.
	 * @param sessionId the session identifier
	 */
	void destroy(String sessionId);

	/**
	 * Returns the IDs of all active sessions.
	 * @return collection of active session IDs
	 */
	Collection<String> activeSessions();

}
