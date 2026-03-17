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
package org.springaicommunity.agent.serve.metrics;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link AgentServeMetrics}.
 */
@DisplayName("AgentServeMetrics Tests")
class AgentServeMetricsTest {

	private SimpleMeterRegistry registry;

	private AgentServeMetrics metrics;

	@BeforeEach
	void setUp() {
		this.registry = new SimpleMeterRegistry();
		this.metrics = new AgentServeMetrics(this.registry);
	}

	@Test
	@DisplayName("Should increment active sessions gauge on create")
	void shouldIncrementActiveSessionsOnCreate() {
		this.metrics.sessionCreated();

		assertThat(this.registry.get("agent.serve.sessions.active").gauge().value()).isEqualTo(1.0);
	}

	@Test
	@DisplayName("Should decrement active sessions gauge on destroy")
	void shouldDecrementActiveSessionsOnDestroy() {
		this.metrics.sessionCreated();
		this.metrics.sessionDestroyed();

		assertThat(this.registry.get("agent.serve.sessions.active").gauge().value()).isEqualTo(0.0);
	}

	@Test
	@DisplayName("Should increment sessions created counter")
	void shouldIncrementSessionsCreatedCounter() {
		this.metrics.sessionCreated();
		this.metrics.sessionCreated();

		assertThat(this.registry.get("agent.serve.sessions.created").counter().count()).isEqualTo(2.0);
	}

	@Test
	@DisplayName("Should increment eviction counter")
	void shouldIncrementEvictionCounter() {
		this.metrics.sessionEvicted();

		assertThat(this.registry.get("agent.serve.sessions.evicted").counter().count()).isEqualTo(1.0);
	}

	@Test
	@DisplayName("Should increment request counter")
	void shouldIncrementRequestCounter() {
		this.metrics.requestStarted();

		assertThat(this.registry.get("agent.serve.requests").counter().count()).isEqualTo(1.0);
	}

	@Test
	@DisplayName("Should record request duration")
	void shouldRecordRequestDuration() {
		Timer.Sample sample = this.metrics.startRequestTimer();
		this.metrics.requestCompleted(sample);

		assertThat(this.registry.get("agent.serve.request.duration").timer().count()).isEqualTo(1);
	}

	@Test
	@DisplayName("Should increment question timeout counter")
	void shouldIncrementQuestionTimeoutCounter() {
		this.metrics.questionTimedOut();

		assertThat(this.registry.get("agent.serve.questions.timed.out").counter().count()).isEqualTo(1.0);
	}

}
