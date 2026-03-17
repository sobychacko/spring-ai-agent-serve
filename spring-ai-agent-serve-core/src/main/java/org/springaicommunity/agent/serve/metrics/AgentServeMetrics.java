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

import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Micrometer metrics for the Spring AI Agent Serve layer.
 *
 * <p>
 * Provides counters, gauges, and timers for session lifecycle, request processing,
 * tool call execution, and question timeout tracking. All metric names are prefixed
 * with {@code agent.serve.}.
 *
 */
public class AgentServeMetrics {

	private static final String PREFIX = "agent.serve.";

	private final MeterRegistry registry;

	private final AtomicInteger activeSessions;

	private final Counter sessionsCreated;

	private final Counter sessionsEvicted;

	private final Counter requests;

	private final Timer requestDuration;

	private final Counter questionsTimedOut;

	public AgentServeMetrics(MeterRegistry registry) {
		this.registry = registry;
		this.activeSessions = registry.gauge(PREFIX + "sessions.active", new AtomicInteger(0));
		this.sessionsCreated = registry.counter(PREFIX + "sessions.created");
		this.sessionsEvicted = registry.counter(PREFIX + "sessions.evicted");
		this.requests = registry.counter(PREFIX + "requests");
		this.requestDuration = registry.timer(PREFIX + "request.duration");
		this.questionsTimedOut = registry.counter(PREFIX + "questions.timed.out");
	}

	public void sessionCreated() {
		this.activeSessions.incrementAndGet();
		this.sessionsCreated.increment();
	}

	public void sessionDestroyed() {
		this.activeSessions.decrementAndGet();
	}

	public void sessionEvicted() {
		this.sessionsEvicted.increment();
	}

	public void requestStarted() {
		this.requests.increment();
	}

	public Timer.Sample startRequestTimer() {
		return Timer.start(this.registry);
	}

	public void requestCompleted(Timer.Sample sample) {
		sample.stop(this.requestDuration);
	}

	public void questionTimedOut() {
		this.questionsTimedOut.increment();
	}

}
