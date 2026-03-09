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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springaicommunity.agent.serve.feedback.ServeQuestionHandlerFactory;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link InMemoryAgentSessionManager}.
 */
@DisplayName("InMemoryAgentSessionManager Tests")
@ExtendWith(MockitoExtension.class)
class InMemoryAgentSessionManagerTest {

	@Mock
	private ChatClient.Builder chatClientBuilder;

	@Mock
	private ChatClient.Builder clonedBuilder;

	@Mock
	private ChatClient chatClient;

	private InMemoryAgentSessionManager manager;

	@BeforeEach
	void setUp() {
		this.manager = new InMemoryAgentSessionManager(this.chatClientBuilder);
	}

	private void wireBuilderChain() {
		when(this.chatClientBuilder.clone()).thenReturn(this.clonedBuilder);
		when(this.clonedBuilder.defaultAdvisors(any(Advisor.class))).thenReturn(this.clonedBuilder);
		when(this.clonedBuilder.build()).thenReturn(this.chatClient);
	}

	@Nested
	@DisplayName("get")
	class Get {

		@Test
		@DisplayName("Should return empty for unknown sessionId")
		void shouldReturnEmptyForUnknown() {
			assertThat(manager.get("unknown")).isEmpty();
		}

		@Test
		@DisplayName("Should return existing session")
		void shouldReturnExistingSession() {
			wireBuilderChain();
			AgentSession created = manager.getOrCreate("s1");

			assertThat(manager.get("s1")).hasValue(created);
		}

	}

	@Nested
	@DisplayName("getOrCreate")
	class GetOrCreate {

		@Test
		@DisplayName("Should create new session for unknown sessionId")
		void shouldCreateNewSession() {
			wireBuilderChain();

			AgentSession session = manager.getOrCreate("new-id");

			assertThat(session).isNotNull();
			assertThat(session.getSessionId()).isEqualTo("new-id");
		}

		@Test
		@DisplayName("Should return same session on subsequent calls")
		void shouldReturnSameSessionOnSubsequentCalls() {
			wireBuilderChain();

			AgentSession session1 = manager.getOrCreate("id-1");
			AgentSession session2 = manager.getOrCreate("id-1");

			assertThat(session1).isSameAs(session2);
		}

		@Test
		@DisplayName("Should clone builder for each new session")
		void shouldCloneBuilderForEachSession() {
			wireBuilderChain();

			manager.getOrCreate("s1");
			manager.getOrCreate("s2");

			verify(chatClientBuilder, times(2)).clone();
		}

	}

	@Nested
	@DisplayName("Question Handler Wiring")
	class QuestionHandlerWiring {

		@Test
		@DisplayName("Should wire AskUserQuestionTool when factory is provided")
		void shouldWireQuestionToolWithFactory() {
			ServeQuestionHandlerFactory factory = new ServeQuestionHandlerFactory(5);
			manager = new InMemoryAgentSessionManager(chatClientBuilder, 500, factory);

			when(chatClientBuilder.clone()).thenReturn(clonedBuilder);
			when(clonedBuilder.defaultAdvisors(any(Advisor.class))).thenReturn(clonedBuilder);
			when(clonedBuilder.defaultTools(any(Object.class))).thenReturn(clonedBuilder);
			when(clonedBuilder.build()).thenReturn(chatClient);

			AgentSession session = manager.getOrCreate("with-question-handler");

			assertThat(session).isNotNull();
			// Verify defaultTools was called (AskUserQuestionTool was added)
			verify(clonedBuilder).defaultTools(any(Object.class));
		}

		@Test
		@DisplayName("Should not wire AskUserQuestionTool when factory is absent")
		void shouldNotWireQuestionToolWithoutFactory() {
			wireBuilderChain();

			manager.getOrCreate("without-question-handler");

			verify(clonedBuilder, times(0)).defaultTools(any());
		}

	}

	@Nested
	@DisplayName("Tool Callback Wrapping")
	class ToolCallbackWrapping {

		@Test
		@DisplayName("Should wrap tools with ObservableToolCallback when tool list is provided")
		void shouldWrapToolsWhenProvided() {
			ToolCallback mockTool = mock(ToolCallback.class);

			manager = new InMemoryAgentSessionManager(chatClientBuilder, 500, null,
					List.of(mockTool));

			when(chatClientBuilder.clone()).thenReturn(clonedBuilder);
			when(clonedBuilder.defaultAdvisors(any(Advisor.class))).thenReturn(clonedBuilder);
			when(clonedBuilder.defaultToolCallbacks(any(ToolCallback[].class))).thenReturn(clonedBuilder);
			when(clonedBuilder.build()).thenReturn(chatClient);

			AgentSession session = manager.getOrCreate("with-tools");

			assertThat(session).isNotNull();
			verify(clonedBuilder).defaultToolCallbacks(any(ToolCallback[].class));
		}

		@Test
		@DisplayName("Should not call defaultToolCallbacks when no tool list is provided")
		void shouldNotWrapToolsWhenAbsent() {
			wireBuilderChain();

			manager.getOrCreate("no-tools");

			verify(clonedBuilder, never()).defaultToolCallbacks(any(ToolCallback[].class));
		}

	}

	@Nested
	@DisplayName("destroy")
	class Destroy {

		@Test
		@DisplayName("Should remove session and not affect other sessions")
		void shouldRemoveSessionAndNotAffectOthers() {
			wireBuilderChain();
			manager.getOrCreate("s1");
			AgentSession s2 = manager.getOrCreate("s2");

			manager.destroy("s1");

			assertThat(manager.activeSessions()).containsExactly("s2");
			assertThat(manager.getOrCreate("s2")).isSameAs(s2);
		}

	}

	@Nested
	@DisplayName("activeSessions")
	class ActiveSessions {

		@Test
		@DisplayName("Should return empty collection initially")
		void shouldReturnEmptyInitially() {
			assertThat(manager.activeSessions()).isEmpty();
		}

		@Test
		@DisplayName("Should return snapshot not live view")
		void shouldReturnSnapshot() {
			wireBuilderChain();
			manager.getOrCreate("s1");
			Collection<String> snapshot = manager.activeSessions();

			manager.getOrCreate("s2");

			assertThat(snapshot).containsExactly("s1");
			assertThat(snapshot).doesNotContain("s2");
		}

	}

	@Nested
	@DisplayName("Thread Safety")
	class ThreadSafety {

		@Test
		@DisplayName("Concurrent getOrCreate with same ID should return same session")
		void concurrentGetOrCreateSameId() throws Exception {
			wireBuilderChain();
			int threadCount = 10;
			CountDownLatch startLatch = new CountDownLatch(1);
			CountDownLatch doneLatch = new CountDownLatch(threadCount);
			Set<AgentSession> sessions = new HashSet<>();
			ExecutorService executor = Executors.newFixedThreadPool(threadCount);

			try {
				for (int i = 0; i < threadCount; i++) {
					executor.submit(() -> {
						try {
							startLatch.await();
							AgentSession session = manager.getOrCreate("shared");
							synchronized (sessions) {
								sessions.add(session);
							}
						}
						catch (InterruptedException e) {
							Thread.currentThread().interrupt();
						}
						finally {
							doneLatch.countDown();
						}
					});
				}
				startLatch.countDown();
				doneLatch.await(5, TimeUnit.SECONDS);

				assertThat(sessions).hasSize(1);
			}
			finally {
				executor.shutdown();
			}
		}

	}

}
