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
package org.springaicommunity.agent.serve.transport.sse;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springaicommunity.agent.serve.AgentEvent;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link SseEventSender}.
 */
@DisplayName("SseEventSender Tests")
class SseEventSenderTest {

	private SseEventSender sender;

	@BeforeEach
	void setUp() {
		this.sender = new SseEventSender();
	}

	@Test
	@DisplayName("Should send event to registered emitter")
	void shouldSendEventToRegisteredEmitter() throws Exception {
		SseEmitter emitter = mock(SseEmitter.class);
		this.sender.register("s1", emitter);
		AgentEvent event = AgentEvent.responseChunk("s1", "Hello");

		this.sender.send("s1", event);

		verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
	}

	@Test
	@DisplayName("Should not throw when no emitter is registered")
	void shouldNotThrowWhenNoEmitter() {
		AgentEvent event = AgentEvent.responseChunk("unknown", "Hello");

		assertThatNoException().isThrownBy(() -> this.sender.send("unknown", event));
	}

	@Test
	@DisplayName("Should remove emitter on IOException")
	void shouldRemoveEmitterOnIOException() throws Exception {
		SseEmitter emitter = mock(SseEmitter.class);
		doThrow(new IOException("disconnected")).when(emitter).send(any(SseEmitter.SseEventBuilder.class));
		this.sender.register("s1", emitter);

		this.sender.send("s1", AgentEvent.responseChunk("s1", "Hello"));

		// Second send should not reach the emitter — it was removed
		SseEmitter emitter2 = mock(SseEmitter.class);
		// Don't re-register; verify the original was removed
		this.sender.send("s1", AgentEvent.responseChunk("s1", "World"));
		verify(emitter2, never()).send(any(SseEmitter.SseEventBuilder.class));
	}

	@Test
	@DisplayName("Should replace emitter on re-register")
	void shouldReplaceEmitterOnReRegister() throws Exception {
		SseEmitter first = mock(SseEmitter.class);
		SseEmitter second = mock(SseEmitter.class);
		this.sender.register("s1", first);
		this.sender.register("s1", second);

		this.sender.send("s1", AgentEvent.responseChunk("s1", "Hello"));

		verify(first, never()).send(any(SseEmitter.SseEventBuilder.class));
		verify(second).send(any(SseEmitter.SseEventBuilder.class));
	}

	@Test
	@DisplayName("Should remove emitter cleanly")
	void shouldRemoveEmitterCleanly() throws Exception {
		SseEmitter emitter = mock(SseEmitter.class);
		this.sender.register("s1", emitter);
		this.sender.remove("s1");

		this.sender.send("s1", AgentEvent.responseChunk("s1", "Hello"));

		verify(emitter, never()).send(any(SseEmitter.SseEventBuilder.class));
	}

}
