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

import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import tools.jackson.databind.json.JsonMapper;

import org.springaicommunity.agent.serve.AgentEvent;
import org.springaicommunity.agent.serve.AgentRequest;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * CLI client that interacts with the agent processor over Kafka.
 *
 * <p>
 * Produces {@link AgentRequest} messages to the {@code agent-requests} topic and
 * consumes {@link AgentEvent} messages from the {@code agent-events} topic. Streaming
 * responses appear token-by-token in the terminal.
 *
 * <p>
 * Usage: Start the {@code serve-kafka-agent-processor} first, then run this application.
 * Type messages at the prompt to interact with the agent.
 *
 */
@SpringBootApplication
public class Application implements CommandLineRunner {

	private final KafkaTemplate<String, String> kafkaTemplate;

	private final JsonMapper jsonMapper;

	private final EventRenderer renderer;

	private final String sessionId = UUID.randomUUID().toString();

	public Application(KafkaTemplate<String, String> kafkaTemplate, JsonMapper jsonMapper,
			EventRenderer renderer) {
		this.kafkaTemplate = kafkaTemplate;
		this.jsonMapper = jsonMapper;
		this.renderer = renderer;
	}

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
		System.out.println("Kafka CLI Client");
		System.out.println("Session ID: " + this.sessionId);
		System.out.println("Type your messages below. Press Ctrl+C to exit.");
		System.out.println();

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
				this.renderer.setResponseLatch(latch);

				AgentRequest request = new AgentRequest(this.sessionId, input);
				String json = this.jsonMapper.writeValueAsString(request);
				this.kafkaTemplate.send("agent-requests", this.sessionId, json);

				latch.await(5, TimeUnit.MINUTES);
			}
		}
	}

	/**
	 * Consumes agent events from Kafka and renders them to the console.
	 */
	@Component
	static class EventRenderer {

		private final JsonMapper jsonMapper;

		private volatile CountDownLatch responseLatch;

		private boolean inResponse = false;

		EventRenderer(JsonMapper jsonMapper) {
			this.jsonMapper = jsonMapper;
		}

		void setResponseLatch(CountDownLatch latch) {
			this.responseLatch = latch;
			this.inResponse = false;
		}

		@KafkaListener(topics = "agent-events", groupId = "agent-cli-client")
		void onEvent(String message) {
			try {
				AgentEvent event = this.jsonMapper.readValue(message, AgentEvent.class);
				render(event);
			}
			catch (Exception ex) {
				System.err.println("[error] Failed to parse event: " + ex.getMessage());
			}
		}

		private void render(AgentEvent event) {
			switch (event.type()) {
				case "RESPONSE_CHUNK" -> {
					if (!this.inResponse) {
						System.out.print("Agent> ");
						this.inResponse = true;
					}
					System.out.print(event.content());
					System.out.flush();
				}
				case "TOOL_CALL_STARTED" -> {
					String toolName = event.metadata() != null
							? String.valueOf(event.metadata().get("toolName"))
							: "unknown";
					System.out.println("  [calling " + toolName + "...]");
				}
				case "TOOL_CALL_COMPLETED" -> {
					String toolName = event.metadata() != null
							? String.valueOf(event.metadata().get("toolName"))
							: "unknown";
					System.out.println("  [" + toolName + " done]");
				}
				case "FINAL_RESPONSE" -> {
					if (!this.inResponse) {
						System.out.print("Agent> ");
					}
					System.out.println();
					System.out.println();
					this.inResponse = false;
					if (this.responseLatch != null) {
						this.responseLatch.countDown();
					}
				}
				case "ERROR" -> {
					System.out.println();
					System.out.println("[error] " + event.content());
					System.out.println();
					this.inResponse = false;
					if (this.responseLatch != null) {
						this.responseLatch.countDown();
					}
				}
				case "QUESTION_REQUIRED" -> {
					System.out.println();
					System.out.println("[question] " + event.metadata());
					System.out.println();
					this.inResponse = false;
					if (this.responseLatch != null) {
						this.responseLatch.countDown();
					}
				}
				default -> { }
			}
		}

	}

}
