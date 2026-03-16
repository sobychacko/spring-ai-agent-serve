# Kafka Agent Processor

Backend agent service that consumes requests from Kafka, processes them through a Spring AI agent, and produces events back to Kafka. No web server — this is a pure consumer/producer service.

This demonstrates the "serve behind a message broker" deployment model where the agent is a backend processing service in an event-driven pipeline.

## Prerequisites

- Java 17+
- Docker (for Kafka)
- `ANTHROPIC_API_KEY` environment variable set

## Start Kafka

From the `examples/kafka/` directory:

```bash
docker compose up -d
```

This starts a single-node Kafka 4.1 broker in KRaft mode on `localhost:9092`. No ZooKeeper needed.

To stop it later:

```bash
docker compose down
```

## Run

From the project root:

```bash
export ANTHROPIC_API_KEY=your-key-here
mvn spring-boot:run -pl examples/kafka/agent-processor
```

The agent processor starts and listens on the `agent-requests` topic. It produces events to the `agent-events` topic.

## Test with the CLI Client

The easiest way to interact with the processor is the [Kafka CLI Client](../cli-client). In a separate terminal:

```bash
mvn spring-boot:run -pl examples/kafka/cli-client
```

## Test with Docker exec

You can also test with Kafka's built-in tools via the running container.

**Watch events** (in one terminal):

```bash
docker exec -it kafka-kafka-1 /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic agent-events
```

**Send a request** (in another terminal):

```bash
echo '{"sessionId":"test-1","message":"What is Spring AI?"}' | \
  docker exec -i kafka-kafka-1 /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 --topic agent-requests
```

Events stream into the consumer terminal as the agent processes the request.

## How It Works

The app defines two beans — a `ChatClient.Builder` with a system prompt and a `List<ToolCallback>` with agent-utils tools. The Kafka starter auto-configures everything else: a `@KafkaListener` that consumes requests, routes them to sessions, streams through the agent, and produces events via `KafkaEventSender`.

The `sessionId` field in each request determines which session handles it. Multiple sessions run concurrently with isolated conversation memory.
