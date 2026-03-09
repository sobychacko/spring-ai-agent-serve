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

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.springaicommunity.agent.serve.AgentEvent;
import org.springaicommunity.agent.serve.AgentRequest;
import org.springaicommunity.agent.serve.QuestionAnswer;
import org.springaicommunity.agent.serve.cli.ConsoleEventRenderer.QuestionPrompt;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/**
 * CLI client that connects to a running serve server over STOMP/WebSocket.
 *
 * <p>
 * This application demonstrates the serve layer's WebSocket transport from a non-browser
 * client. It connects to the serve layer server's native WebSocket endpoint
 * ({@code /ws/websocket} — the SockJS convention), subscribes to the agent's event
 * topic, and provides an interactive stdin loop for sending messages.
 *
 * <p>
 * Usage: Start the {@code serve-chatbot-demo} server first, then run this application.
 * Type messages at the prompt to interact with the agent. Streaming responses appear
 * token-by-token in the terminal. When the agent asks a question, numbered options are
 * displayed and you can type your answer.
 *
 * <p>
 * The thread model is: main thread reads stdin; STOMP callback thread renders events to
 * console. A {@link CountDownLatch} coordinates: the callback counts down on
 * {@code FINAL_RESPONSE}/{@code ERROR}/{@code QUESTION_REQUIRED}, and the main thread
 * awaits before prompting for the next input.
 */
@SpringBootApplication
public class Application implements CommandLineRunner {

	@Value("${serve.server.url:ws://localhost:8080/ws/websocket}")
	private String serverUrl;

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
		String sessionId = UUID.randomUUID().toString();

		BlockingQueue<QuestionPrompt> questionQueue = new LinkedBlockingQueue<>();
		ConsoleEventRenderer renderer = new ConsoleEventRenderer(questionQueue);

		// Connect to the serve layer server
		WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
		stompClient.setMessageConverter(new JacksonJsonMessageConverter());

		System.out.println("Connecting to " + this.serverUrl + " ...");

		StompSession session = stompClient
			.connectAsync(this.serverUrl, new StompSessionHandlerAdapter() {
				@Override
				public void handleException(StompSession session, StompCommand command, StompHeaders headers,
						byte[] payload, Throwable exception) {
					System.err.println("[error] STOMP error: " + exception.getMessage());
				}

				@Override
				public void handleTransportError(StompSession session, Throwable exception) {
					System.err.println("[error] Transport error: " + exception.getMessage());
				}
			})
			.get(10, TimeUnit.SECONDS);

		System.out.println("Connected. Session ID: " + sessionId);

		// Subscribe to events for this session
		session.subscribe("/topic/agent/" + sessionId, new StompFrameHandler() {
			@Override
			public Type getPayloadType(StompHeaders headers) {
				return AgentEvent.class;
			}

			@Override
			public void handleFrame(StompHeaders headers, Object payload) {
				renderer.handleEvent((AgentEvent) payload);
			}
		});

		System.out.println("Type your messages below. Press Ctrl+C to exit.");
		System.out.println();

		// Main stdin loop
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
				renderer.setResponseLatch(latch);

				// Send the message
				session.send("/app/agent", new AgentRequest(sessionId, input));

				// Wait for FINAL_RESPONSE, ERROR, or QUESTION_REQUIRED
				while (true) {
					boolean completed = latch.await(100, TimeUnit.MILLISECONDS);

					// Check if a question arrived
					QuestionPrompt question = questionQueue.poll();
					if (question != null) {
						String answer = handleQuestion(scanner, question);

						Map<String, String> answers = buildAnswers(question, answer);
						session.send("/app/agent/answer",
								new QuestionAnswer(sessionId, question.questionId(), answers));

						// Reset latch for the continued response
						latch = new CountDownLatch(1);
						renderer.setResponseLatch(latch);
					}

					if (completed && questionQueue.isEmpty()) {
						break;
					}
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	private String handleQuestion(Scanner scanner, QuestionPrompt question) {
		if (!scanner.hasNextLine()) {
			return "";
		}
		return scanner.nextLine().trim();
	}

	@SuppressWarnings("unchecked")
	private Map<String, String> buildAnswers(QuestionPrompt question, String answer) {
		Map<String, String> answers = new LinkedHashMap<>();
		Object questionsObj = question.questions();

		if (questionsObj instanceof List<?> questionsList) {
			for (Object q : questionsList) {
				if (q instanceof Map<?, ?> questionMap) {
					String questionText = (String) questionMap.get("question");
					List<Map<String, String>> options = (List<Map<String, String>>) questionMap.get("options");

					// Try to parse as a number (option selection)
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

}
