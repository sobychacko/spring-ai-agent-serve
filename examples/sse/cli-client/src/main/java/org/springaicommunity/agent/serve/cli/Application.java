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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springaicommunity.agent.serve.AgentEvent;
import org.springaicommunity.agent.serve.cli.ConsoleEventRenderer.QuestionPrompt;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * CLI client that connects to a running serve server over SSE.
 *
 * <p>
 * This application demonstrates the serve layer's SSE transport from a non-browser
 * client. It opens an SSE event stream to receive agent events and uses REST POST
 * endpoints to send messages and answer questions.
 *
 * <p>
 * Usage: Start the {@code serve-sse-chatbot-demo} server first, then run this application.
 * Type messages at the prompt to interact with the agent. Streaming responses appear
 * token-by-token in the terminal. When the agent asks a question, numbered options are
 * displayed and you can type your answer.
 *
 * <p>
 * The thread model is: main thread reads stdin; a background thread reads the SSE stream
 * and renders events to console. A {@link CountDownLatch} coordinates: the background
 * thread counts down on {@code FINAL_RESPONSE}/{@code ERROR}/{@code QUESTION_REQUIRED},
 * and the main thread awaits before prompting for the next input.
 */
@SpringBootApplication
public class Application implements CommandLineRunner {

	@Value("${serve.server.url:http://localhost:8080}")
	private String serverUrl;

	private final ObjectMapper objectMapper = new ObjectMapper();

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
		BlockingQueue<QuestionPrompt> questionQueue = new LinkedBlockingQueue<>();
		ConsoleEventRenderer renderer = new ConsoleEventRenderer(questionQueue);

		RestClient restClient = RestClient.builder().baseUrl(this.serverUrl).build();

		// Open SSE connection with sessionId "new" to get a generated session ID
		System.out.println("Connecting to " + this.serverUrl + " ...");

		String sseUrl = this.serverUrl + "/api/agent/sessions/new/events";
		HttpURLConnection connection = (HttpURLConnection) URI.create(sseUrl).toURL().openConnection();
		connection.setRequestProperty("Accept", "text/event-stream");
		connection.setDoInput(true);
		connection.connect();

		BufferedReader sseReader = new BufferedReader(new InputStreamReader(connection.getInputStream()));

		// Read the first event to get the session ID
		String sessionId = readSessionId(sseReader);
		System.out.println("Connected. Session ID: " + sessionId);

		// Start background thread to read SSE events
		final String sid = sessionId;
		Thread sseThread = new Thread(() -> readSseEvents(sseReader, renderer, sid));
		sseThread.setDaemon(true);
		sseThread.setName("sse-reader");
		sseThread.start();

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

				// Send the message via REST POST
				restClient.post()
					.uri("/api/agent/sessions/{sessionId}/messages", sid)
					.contentType(MediaType.APPLICATION_JSON)
					.body(Map.of("sessionId", sid, "message", input))
					.retrieve()
					.toBodilessEntity();

				// Wait for FINAL_RESPONSE, ERROR, or QUESTION_REQUIRED
				while (true) {
					boolean completed = latch.await(100, TimeUnit.MILLISECONDS);

					// Check if a question arrived
					QuestionPrompt question = questionQueue.poll();
					if (question != null) {
						String answer = handleQuestion(scanner);

						Map<String, String> answers = buildAnswers(question, answer);

						restClient.post()
							.uri("/api/agent/sessions/{sessionId}/answers", sid)
							.contentType(MediaType.APPLICATION_JSON)
							.body(Map.of("sessionId", sid, "questionId", question.questionId(), "answers", answers))
							.retrieve()
							.toBodilessEntity();

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

	private String readSessionId(BufferedReader reader) throws Exception {
		String line;
		String eventType = null;
		StringBuilder data = new StringBuilder();

		while ((line = reader.readLine()) != null) {
			if (line.startsWith("event:")) {
				eventType = line.substring(6).trim();
			}
			else if (line.startsWith("data:")) {
				data.append(line.substring(5).trim());
			}
			else if (line.isEmpty() && eventType != null) {
				if ("SESSION_CREATED".equals(eventType)) {
					AgentEvent event = this.objectMapper.readValue(data.toString(), AgentEvent.class);
					return (String) event.metadata().get("sessionId");
				}
				eventType = null;
				data.setLength(0);
			}
		}
		throw new IllegalStateException("SSE stream ended before SESSION_CREATED event");
	}

	private void readSseEvents(BufferedReader reader, ConsoleEventRenderer renderer, String sessionId) {
		try {
			String line;
			String eventType = null;
			StringBuilder data = new StringBuilder();

			while ((line = reader.readLine()) != null) {
				if (line.startsWith("event:")) {
					eventType = line.substring(6).trim();
				}
				else if (line.startsWith("data:")) {
					data.append(line.substring(5).trim());
				}
				else if (line.isEmpty() && eventType != null) {
					try {
						AgentEvent event = this.objectMapper.readValue(data.toString(), AgentEvent.class);
						renderer.handleEvent(event);
					}
					catch (Exception ex) {
						System.err.println("[error] Failed to parse SSE event: " + ex.getMessage());
					}
					eventType = null;
					data.setLength(0);
				}
			}
		}
		catch (Exception ex) {
			System.err.println("[error] SSE stream error: " + ex.getMessage());
		}
	}

	private String handleQuestion(Scanner scanner) {
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
