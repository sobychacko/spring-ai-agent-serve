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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link AgentSession}.
 */
@DisplayName("AgentSession Tests")
@ExtendWith(MockitoExtension.class)
class AgentSessionTest {

	@Mock
	private ChatClient chatClient;

	@Mock
	private ChatClient.ChatClientRequestSpec requestSpec;

	@Mock
	private ChatClient.StreamResponseSpec streamResponseSpec;

	@Mock
	private ChatMemory chatMemory;

	private AgentSession session;

	@BeforeEach
	void setUp() {
		this.session = new AgentSession("test-session", this.chatClient, this.chatMemory);
	}

	private void wireStreamingChain(Flux<ChatResponse> responseFlux) {
		when(this.chatClient.prompt(anyString())).thenReturn(this.requestSpec);
		when(this.requestSpec.stream()).thenReturn(this.streamResponseSpec);
		when(this.streamResponseSpec.chatResponse()).thenReturn(responseFlux);
	}

	private ChatResponse textChunk(String text) {
		AssistantMessage message = new AssistantMessage(text);
		Generation generation = new Generation(message);
		return new ChatResponse(List.of(generation));
	}

	@AfterEach
	void tearDown() {
		this.session.close();
	}

	@Nested
	@DisplayName("SubmitStream")
	class SubmitStream {

		@Test
		@DisplayName("Should emit RESPONSE_CHUNK events for text and FINAL_RESPONSE at end")
		void shouldStreamTextChunks() throws Exception {
			Flux<ChatResponse> responses = Flux.just(textChunk("Hello"), textChunk(" World"));
			wireStreamingChain(responses);

			List<AgentEvent> events = Collections.synchronizedList(new ArrayList<>());
			CountDownLatch latch = new CountDownLatch(1);

			session.submitStream(new AgentRequest("test-session", "Hi"), event -> {
				events.add(event);
				if ("FINAL_RESPONSE".equals(event.type())) {
					latch.countDown();
				}
			});

			latch.await(5, TimeUnit.SECONDS);

			assertThat(events).extracting(AgentEvent::type)
				.containsExactly("RESPONSE_CHUNK", "RESPONSE_CHUNK", "FINAL_RESPONSE");
			assertThat(events.get(0).content()).isEqualTo("Hello");
			assertThat(events.get(1).content()).isEqualTo(" World");
			assertThat(events.get(2).content()).isEqualTo("Hello World");
		}

		@Test
		@DisplayName("Should pass message to chatClient.prompt()")
		void shouldPassMessageToChatClient() throws Exception {
			wireStreamingChain(Flux.just(textChunk("ok")));
			CountDownLatch latch = new CountDownLatch(1);

			session.submitStream(new AgentRequest("test-session", "What is Java?"), event -> {
				if ("FINAL_RESPONSE".equals(event.type())) {
					latch.countDown();
				}
			});

			latch.await(5, TimeUnit.SECONDS);
			verify(chatClient).prompt("What is Java?");
		}

		@Test
		@DisplayName("Should emit ERROR event when stream fails")
		void shouldEmitErrorOnStreamFailure() throws Exception {
			wireStreamingChain(Flux.error(new RuntimeException("LLM failed")));

			List<AgentEvent> events = Collections.synchronizedList(new ArrayList<>());
			CountDownLatch latch = new CountDownLatch(1);

			session.submitStream(new AgentRequest("test-session", "Hi"), event -> {
				events.add(event);
				if ("ERROR".equals(event.type())) {
					latch.countDown();
				}
			});

			latch.await(5, TimeUnit.SECONDS);

			assertThat(events).extracting(AgentEvent::type).contains("ERROR");
			assertThat(events.stream().filter(e -> "ERROR".equals(e.type())).findFirst().get().content())
				.isEqualTo("LLM failed");
		}

	}

	@Nested
	@DisplayName("Serial Execution")
	class SerialExecution {

		@Test
		@DisplayName("Should execute streaming requests sequentially within same session")
		void shouldExecuteSequentially() throws Exception {
			List<String> executionOrder = Collections.synchronizedList(new ArrayList<>());

			Flux<ChatResponse> slowResponse = Flux.defer(() -> {
				executionOrder.add("start");
				return Flux.just(textChunk("response")).doOnComplete(() -> {
					try {
						Thread.sleep(50);
					}
					catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
					executionOrder.add("end");
				});
			});

			wireStreamingChain(slowResponse);

			CountDownLatch latch = new CountDownLatch(2);

			session.submitStream(new AgentRequest("test-session", "msg1"), event -> {
				if ("FINAL_RESPONSE".equals(event.type())) {
					latch.countDown();
				}
			});
			session.submitStream(new AgentRequest("test-session", "msg2"), event -> {
				if ("FINAL_RESPONSE".equals(event.type())) {
					latch.countDown();
				}
			});

			latch.await(10, TimeUnit.SECONDS);

			// Both should have run: start, end, start, end
			assertThat(executionOrder).hasSize(4);
			assertThat(executionOrder.get(0)).isEqualTo("start");
			assertThat(executionOrder.get(1)).isEqualTo("end");
			assertThat(executionOrder.get(2)).isEqualTo("start");
			assertThat(executionOrder.get(3)).isEqualTo("end");
		}

	}

	@Nested
	@DisplayName("Close")
	class CloseTests {

		@Test
		@DisplayName("Should complete in-flight streaming request before shutting down")
		void shouldCompleteInFlightRequest() throws Exception {
			wireStreamingChain(Flux.just(textChunk("Hello")));

			List<AgentEvent> events = Collections.synchronizedList(new ArrayList<>());
			CountDownLatch latch = new CountDownLatch(1);

			session.submitStream(new AgentRequest("test-session", "Hi"), event -> {
				events.add(event);
				if ("FINAL_RESPONSE".equals(event.type())) {
					latch.countDown();
				}
			});

			session.close();
			latch.await(5, TimeUnit.SECONDS);

			assertThat(events).extracting(AgentEvent::type).contains("FINAL_RESPONSE");
		}

	}

}
