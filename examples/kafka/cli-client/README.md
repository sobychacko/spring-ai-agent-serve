# Kafka CLI Client

Terminal client that interacts with the agent processor over Kafka. Produces requests to the `agent-requests` topic and consumes events from the `agent-events` topic. Streaming responses appear token-by-token in the terminal.

## Prerequisites

- Java 17+
- Docker (for Kafka)
- A running Kafka agent processor (see [Kafka Agent Processor](../agent-processor))

## Run

**Step 1:** Start Kafka from the `examples/kafka/` directory:

```bash
docker compose up -d
```

**Step 2:** Start the agent processor in one terminal:

```bash
export ANTHROPIC_API_KEY=your-key-here
mvn spring-boot:run -pl examples/kafka/agent-processor
```

**Step 3:** In a separate terminal, start the CLI client:

```bash
mvn spring-boot:run -pl examples/kafka/cli-client
```

The client generates a session ID and gives you a `You>` prompt. Type messages to interact with the agent.

## What It Shows

- The full Kafka round-trip: stdin -> Kafka request topic -> agent processor -> Kafka event topic -> terminal
- Streaming responses rendered token-by-token
- Tool call indicators when the agent invokes tools
- Session isolation via the `sessionId` Kafka message key
- No HTTP involved — the client and processor communicate entirely through Kafka

## How It Works

The client uses Spring Kafka's `KafkaTemplate` to produce `AgentRequest` JSON messages to the `agent-requests` topic with the session ID as the message key. A `@KafkaListener` on the `agent-events` topic receives `AgentEvent` messages and renders them to the console. A `CountDownLatch` coordinates the main stdin thread with the background Kafka consumer thread.
