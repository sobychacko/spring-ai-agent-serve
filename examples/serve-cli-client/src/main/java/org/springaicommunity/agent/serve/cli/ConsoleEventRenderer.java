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
package org.springaicommunity.agent.serve.cli;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;

import org.springaicommunity.agent.serve.AgentEvent;

/**
 * Renders {@link AgentEvent} instances to the terminal and coordinates question handling
 * with the main thread.
 *
 * <p>
 * This class is called from the STOMP callback thread. It prints streaming text, tool
 * call status, errors, and question prompts. When a question arrives, it stores the
 * question data in a shared {@link BlockingQueue} so the main stdin-reading thread can
 * collect the user's answer.
 */
class ConsoleEventRenderer {

	private final BlockingQueue<QuestionPrompt> questionQueue;

	private volatile CountDownLatch responseLatch;

	ConsoleEventRenderer(BlockingQueue<QuestionPrompt> questionQueue) {
		this.questionQueue = questionQueue;
	}

	void setResponseLatch(CountDownLatch latch) {
		this.responseLatch = latch;
	}

	@SuppressWarnings("unchecked")
	void handleEvent(AgentEvent event) {
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

				QuestionPrompt prompt = new QuestionPrompt(questionId, questionsObj);
				this.questionQueue.offer(prompt);
			}
			case "FINAL_RESPONSE" -> {
				System.out.println();
				countDown();
			}
			case "ERROR" -> {
				System.err.println("\n[error] " + event.content());
				countDown();
			}
			default -> {
				// Ignore unknown event types
			}
		}
	}

	private void countDown() {
		CountDownLatch latch = this.responseLatch;
		if (latch != null) {
			latch.countDown();
		}
	}

	private String getToolName(AgentEvent event) {
		Object name = event.metadata().get("toolName");
		return name != null ? name.toString() : "unknown";
	}

	/**
	 * Holds question data passed from the STOMP callback thread to the main stdin thread.
	 */
	record QuestionPrompt(String questionId, Object questions) {
	}

}
