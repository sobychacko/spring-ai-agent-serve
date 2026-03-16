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
package org.springaicommunity.agent.serve.health;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springaicommunity.agent.serve.AgentSessionManager;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link AgentServeHealthIndicator}.
 */
@DisplayName("AgentServeHealthIndicator Tests")
@ExtendWith(MockitoExtension.class)
class AgentServeHealthIndicatorTest {

	@Mock
	private AgentSessionManager sessionManager;

	@Test
	@DisplayName("Should report UP with active session count")
	void shouldReportUpWithSessionCount() {
		when(this.sessionManager.activeSessions()).thenReturn(List.of("s1", "s2", "s3"));

		AgentServeHealthIndicator indicator = new AgentServeHealthIndicator(this.sessionManager);
		Health health = indicator.health();

		assertThat(health.getStatus()).isEqualTo(Status.UP);
		assertThat(health.getDetails()).containsEntry("activeSessions", 3);
	}

	@Test
	@DisplayName("Should report zero sessions when none active")
	void shouldReportZeroSessions() {
		when(this.sessionManager.activeSessions()).thenReturn(List.of());

		AgentServeHealthIndicator indicator = new AgentServeHealthIndicator(this.sessionManager);
		Health health = indicator.health();

		assertThat(health.getStatus()).isEqualTo(Status.UP);
		assertThat(health.getDetails()).containsEntry("activeSessions", 0);
	}

}
