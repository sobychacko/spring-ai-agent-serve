# AMQP CLI Client

Terminal client that interacts with the agent processor over RabbitMQ. Produces requests to the `agent-requests` queue and consumes events from a session-specific queue bound to the `agent-events` topic exchange. Streaming responses appear token-by-token in the terminal.

## Prerequisites

- Java 17+
- Docker (for RabbitMQ)
- A running AMQP agent processor (see [AMQP Agent Processor](../agent-processor))

## Run

**Step 1:** Start RabbitMQ from the `examples/amqp/` directory:

```bash
docker compose up -d
```

**Step 2:** Start the agent processor in one terminal:

```bash
export ANTHROPIC_API_KEY=your-key-here
mvn spring-boot:run -pl examples/amqp/agent-processor
```

**Step 3:** In a separate terminal, start the CLI client:

```bash
mvn spring-boot:run -pl examples/amqp/cli-client
```

The client generates a session ID and gives you a `You>` prompt. Type messages to interact with the agent.

## What It Shows

- The full AMQP round-trip: stdin -> RabbitMQ request queue -> agent processor -> RabbitMQ event exchange -> terminal
- Streaming responses rendered token-by-token
- Tool call indicators when the agent invokes tools
- Session isolation via the session ID routing key
- No HTTP involved — the client and processor communicate entirely through RabbitMQ

## How It Works

The client sends `AgentRequest` JSON messages to the `agent-requests` queue via `RabbitTemplate`. On startup, it declares an exclusive, auto-delete queue bound to the `agent-events` topic exchange with its session ID as the routing key — this ensures it only receives events for its own session. A `@RabbitListener` on that queue receives `AgentEvent` messages and renders them to the console. A `CountDownLatch` coordinates the main stdin thread with the background AMQP consumer thread.
