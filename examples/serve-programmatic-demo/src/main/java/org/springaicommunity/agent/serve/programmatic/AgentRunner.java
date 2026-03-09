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
package org.springaicommunity.agent.serve.programmatic;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.springaicommunity.agent.serve.AgentEvent;
import org.springaicommunity.agent.serve.AgentRequest;
import org.springaicommunity.agent.serve.AgentSession;
import org.springaicommunity.agent.serve.AgentSessionManager;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Interactive command-line runner that drives the agent serve programmatically.
 *
 * <p>
 * This runner demonstrates how to use the serve layer's core API without any transport layer.
 * It creates an {@link AgentSession} via the auto-configured
 * {@link AgentSessionManager}, reads user input from stdin, and calls
 * {@link AgentSession#submitStream(AgentRequest, Consumer)} with a console-rendering
 * event handler.
 *
 * <p>
 * The event handler prints streaming text, tool call status, and question prompts to the
 * terminal. When a question arrives, the handler signals the main thread via a
 * {@link BlockingQueue}, which reads the user's answer from stdin and calls
 * {@link AgentSession#resolveQuestion(String, Map)} to unblock the agent.
 */
@Component
class AgentRunner implements CommandLineRunner {

	private final AgentSessionManager sessionManager;

	AgentRunner(AgentSessionManager sessionManager) {
		this.sessionManager = sessionManager;
	}

	@Override
	public void run(String... args) throws Exception {
		String sessionId = UUID.randomUUID().toString();
		AgentSession session = this.sessionManager.getOrCreate(sessionId);

		System.out.println("Agent session created: " + sessionId);
		System.out.println("Type your messages below. Press Ctrl+C to exit.");
		System.out.println();

		BlockingQueue<QuestionData> questionQueue = new LinkedBlockingQueue<>();

		try (Scanner scanner = new Scanner(System.in)) {
			while (true) {
				System.out.print("You> ");
				System.out.flush();

				if (!scanner.hasNextLine()) {
					break;
				}
				String input = scanner.nextLine().trim();
				if (input.isEmpty()) {
					continue;
				}

				CountDownLatch latch = new CountDownLatch(1);

				Consumer<AgentEvent> handler = createEventHandler(latch, questionQueue);
				session.submitStream(new AgentRequest(sessionId, input), handler);

				// Wait for completion, handling questions along the way
				while (true) {
					boolean completed = latch.await(100, TimeUnit.MILLISECONDS);

					QuestionData question = questionQueue.poll();
					if (question != null) {
						String answer = readAnswer(scanner);
						Map<String, String> answers = buildAnswers(question, answer);
						session.resolveQuestion(question.questionId(), answers);

						// Reset latch for the continued response
						latch = new CountDownLatch(1);
						CountDownLatch newLatch = latch;
						// Update the handler's latch reference — the session's executor thread
						// will pick up the new latch via the volatile field in the holder
						question.latchHolder()[0] = newLatch;
					}

					if (completed && questionQueue.isEmpty()) {
						break;
					}
				}
			}
		}
		finally {
			this.sessionManager.destroy(sessionId);
		}
	}

	@SuppressWarnings("unchecked")
	private Consumer<AgentEvent> createEventHandler(CountDownLatch initialLatch,
			BlockingQueue<QuestionData> questionQueue) {
		// Use a single-element array as a mutable holder for the latch
		final CountDownLatch[] latchHolder = { initialLatch };

		return event -> {
			switch (event.type()) {
				case "RESPONSE_CHUNK" -> {
					if (event.content() != null) {
						System.out.print(event.content());
						System.out.flush();
					}
				}
				case "TOOL_CALL_STARTED" -> {
					String toolName = getToolName(event);
					System.out.println("\n  [tool] Calling " + toolName + "...");
				}
				case "TOOL_CALL_COMPLETED" -> {
					String toolName = getToolName(event);
					System.out.println("  [done] Used " + toolName);
				}
				case "QUESTION_REQUIRED" -> {
					String questionId = (String) event.metadata().get("questionId");
					Object questionsObj = event.metadata().get("questions");
					System.out.println();
					System.out.println("--- The agent has a question ---");

					if (questionsObj instanceof List<?> questionsList) {
						for (Object q : questionsList) {
							if (q instanceof Map<?, ?> questionMap) {
								String questionText = (String) questionMap.get("question");
								List<Map<String, String>> options = (List<Map<String, String>>) questionMap
									.get("options");
								System.out.println("  " + questionText);
								if (options != null) {
									for (int i = 0; i < options.size(); i++) {
										Map<String, String> option = options.get(i);
										System.out.println("    " + (i + 1) + ") " + option.get("label") + " - "
												+ option.get("description"));
									}
								}
							}
						}
					}
					System.out.println("--------------------------------");
					System.out.print("Your answer: ");
					System.out.flush();

					questionQueue.offer(new QuestionData(questionId, questionsObj, latchHolder));
				}
				case "FINAL_RESPONSE" -> {
					System.out.println();
					latchHolder[0].countDown();
				}
				case "ERROR" -> {
					System.err.println("\n[error] " + event.content());
					latchHolder[0].countDown();
				}
				default -> {
					// Ignore unknown event types
				}
			}
		};
	}

	private String readAnswer(Scanner scanner) {
		if (!scanner.hasNextLine()) {
			return "";
		}
		return scanner.nextLine().trim();
	}

	@SuppressWarnings("unchecked")
	private Map<String, String> buildAnswers(QuestionData question, String answer) {
		Map<String, String> answers = new LinkedHashMap<>();
		Object questionsObj = question.questionsObj();

		if (questionsObj instanceof List<?> questionsList) {
			for (Object q : questionsList) {
				if (q instanceof Map<?, ?> questionMap) {
					String questionText = (String) questionMap.get("question");
					List<Map<String, String>> options = (List<Map<String, String>>) questionMap.get("options");

					String resolvedAnswer = answer;
					if (options != null) {
						try {
							int choice = Integer.parseInt(answer);
							if (choice >= 1 && choice <= options.size()) {
								resolvedAnswer = options.get(choice - 1).get("label");
							}
						}
						catch (NumberFormatException ex) {
							// Use the raw text as the answer
						}
					}
					answers.put(questionText, resolvedAnswer);
				}
			}
		}

		return answers;
	}

	private String getToolName(AgentEvent event) {
		Object name = event.metadata().get("toolName");
		return name != null ? name.toString() : "unknown";
	}

	private record QuestionData(String questionId, Object questionsObj, CountDownLatch[] latchHolder) {
	}

}
