# AMQP Agent Processor

Backend agent service that consumes requests from RabbitMQ, processes them through a Spring AI agent, and produces events back to a topic exchange. No web server — this is a pure consumer/producer service.

This demonstrates the "serve behind a message broker" deployment model where the agent is a backend processing service in an event-driven pipeline.

## Prerequisites

- Java 17+
- Docker (for RabbitMQ)
- `ANTHROPIC_API_KEY` environment variable set

## Start RabbitMQ

From the `examples/amqp/` directory:

```bash
docker compose up -d
```

This starts a RabbitMQ 4 broker with the management plugin on `localhost:5672`. The management UI is available at `http://localhost:15672` (guest/guest).

To stop it later:

```bash
docker compose down
```

## Run

From the project root:

```bash
export ANTHROPIC_API_KEY=your-key-here
mvn spring-boot:run -pl examples/amqp/agent-processor
```

The agent processor starts and listens on the `agent-requests` queue. It produces events to the `agent-events` topic exchange with the session ID as the routing key.

## Test with the CLI Client

The easiest way to interact with the processor is the [AMQP CLI Client](../cli-client). In a separate terminal:

```bash
mvn spring-boot:run -pl examples/amqp/cli-client
```

## How It Works

The app defines two beans — a `ChatClient.Builder` with a system prompt and a `List<ToolCallback>` with agent-utils tools. The AMQP starter auto-configures everything else: a `@RabbitListener` that consumes requests, routes them to sessions, streams through the agent, and produces events via `AmqpEventSender`.

The AMQP topology is declared automatically on startup:
- **`agent-requests`** — durable queue for inbound user messages
- **`agent-answers`** — durable queue for inbound question answers
- **`agent-events`** — topic exchange for outbound agent events (session ID as routing key)

The `sessionId` field in each request determines which session handles it. Multiple sessions run concurrently with isolated conversation memory.
