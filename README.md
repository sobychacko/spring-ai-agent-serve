# Spring AI Agent Serve

Building blocks for assembling your own agent runtime on Spring AI — sessions, streaming, tool call events, and transport for multi-user applications.

## The Problem

A Spring AI agent is a `ChatClient` with tools. It works well in a single-process application. The challenge comes when multiple users need to interact with that agent over the network — through a browser, CLI, or mobile app.

For plain chat, a REST controller returning `Flux<String>` is enough. But agents are different:

- **Tool calls create silent gaps.** Spring AI's streaming gives you tokens in real time as the LLM generates text. But when the agent decides to call a tool, the stream pauses — Spring AI executes the tool internally and the subscriber sees nothing until the LLM resumes generating. Without tool call events (`TOOL_CALL_STARTED`/`TOOL_CALL_COMPLETED`), the UI shows streaming text that suddenly freezes for several seconds with no indication of why.
- **Multiple users need isolation.** Two users hitting the same endpoint shouldn't see each other's conversation history or interfere with each other's tool executions.
- **Concurrent requests corrupt state.** Two messages to the same session at once can interleave tool executions and corrupt conversation memory.
- **Agents ask questions back.** When an agent calls `AskUserQuestionTool` mid-stream, the user needs to answer before the agent can continue. Coordinating this over a network — push the question to the browser, block the agent thread, wait for the answer, resume — is tricky to get right.

If you have three teams building Spring AI agents (sales, supply chain, billing), each team ends up writing its own session isolation, request serialization, streaming event framing, and transport plumbing. Three slightly different implementations, three different threading approaches.

This project packages those common concerns so teams can focus on their agent's domain logic instead.

## Landscape

The serving layer — session management, streaming, transport, and interactive feedback — is a distinct concern from agent construction. Most agent frameworks focus on the latter. The industry has converged on a common set of capabilities for the former, delivered either as managed platforms or as libraries.

### Managed Platforms

Cloud providers offer hosted agent runtime infrastructure as part of their AI platforms:

- [**AWS Bedrock AgentCore**](https://aws.amazon.com/bedrock/agentcore/) — a managed runtime with session isolation, short-term and long-term memory stores, an agent gateway for protocol support (MCP, A2A), identity management, and observability. Supports agents built with multiple frameworks (CrewAI, LangGraph, Strands, and others).

- [**Google Vertex AI Agent Engine**](https://cloud.google.com/vertex-ai/generative-ai/docs/agent-engine/overview) — managed deployment with session management, a memory bank for cross-session personalization, code execution sandboxing, and built-in evaluation. Supports agents built with LangChain, LangGraph, Google ADK, and other frameworks.

- [**Microsoft Foundry Agent Service**](https://learn.microsoft.com/en-us/azure/foundry/agents/overview) — managed agent hosting with conversation management, tool orchestration, content safety, and identity integration. Supports agents built with Microsoft Agent Framework as well as third-party frameworks.

- [**LangGraph Platform**](https://langchain-ai.github.io/langgraph/concepts/langgraph_platform/) — per-thread isolation, multiple streaming modes, double-texting handling, human-in-the-loop endpoints, and background task queues. Python-only, designed for LangGraph state machine-based agents.

These platforms share a common set of capabilities:

| Capability | [Bedrock AgentCore](https://aws.amazon.com/bedrock/agentcore/) | [Vertex Agent Engine](https://cloud.google.com/vertex-ai/generative-ai/docs/agent-engine/overview) | [Foundry Agent Service](https://learn.microsoft.com/en-us/azure/foundry/agents/overview) | [LangGraph Platform](https://langchain-ai.github.io/langgraph/concepts/langgraph_platform/) | **spring-ai-agent-serve** |
|---|---|---|---|---|---|
| Session isolation | Managed runtime | Session management | Conversation management | Per-thread isolation | Per-session `ChatClient` via `clone()` |
| Conversation memory | Short-term + long-term stores | Memory bank | Conversation history | Checkpointed state | `MessageWindowChatMemory` per session |
| Streaming events | Event streaming | Streaming responses | Streaming | Multiple streaming modes | `RESPONSE_CHUNK`, `TOOL_CALL_*`, `FINAL_RESPONSE` |
| Human-in-the-loop | Tool approval (approve/reject) | Tool confirmation (structured input) | Tool approval (approve/reject) | Freeform (`interrupt()` + resume) | Freeform (`AskUserQuestionTool` bridge) |
| Transport | Agent gateway (MCP, A2A) | REST API | REST API | REST API | SSE, WebSocket, STOMP, Kafka, AMQP |
| Request serialization | Managed | Managed | Managed | Double-texting handling | Single-threaded executor per session |

A note on human-in-the-loop: most platforms provide tool approval gates — the agent proposes a tool call and the user approves or rejects it. This is useful for safety but limited in scope. A different capability is interactive feedback, where the agent asks the user a freeform question mid-execution (e.g., "Which database should I migrate — staging or production?"), pauses, and resumes when the answer arrives. LangGraph supports this via its `interrupt()` function. This project provides the same capability for Spring AI agents via the `AskUserQuestionTool` bridge, which coordinates the blocking agent thread with asynchronous client transport using `CompletableFuture`.

### Agent Frameworks

Agent frameworks on the JVM focus on agent construction — models, tools, memory, orchestration. [Spring AI](https://docs.spring.io/spring-ai/reference/), [LangChain4j](https://docs.langchain4j.dev/), [Semantic Kernel](https://learn.microsoft.com/en-us/semantic-kernel/overview/), [Embabel](https://github.com/embabel/embabel-agent), and [Koog](https://github.com/JetBrains/koog) all provide excellent tooling for building agents, but don't include a reusable serving layer for sessions and transport. This project aims to provide that layer for the Spring AI ecosystem, so teams don't have to build it from scratch.

### Why "Serve"

The term "serve" has an established meaning in the ML and AI ecosystem — it refers to the infrastructure layer that hosts a model or agent and exposes it to clients over the network. [TensorFlow Serving](https://github.com/tensorflow/serving), [Ray Serve](https://docs.ray.io/en/latest/serve/index.html), [BentoML](https://github.com/bentoml/BentoML), and [LangServe](https://github.com/langchain-ai/langserve) all use this convention. These projects differ in what they serve (trained models, inference pipelines, LangChain runnables), but they share the same architectural role: accept requests, manage execution, deliver responses.

This project borrows the same naming convention. The "serve" in the name signals the architectural role: it sits between clients and a Spring AI `ChatClient`, handling sessions, streaming, and transport so the agent can be reached over the network.

## What It Does

- **Exposes a ChatClient to remote clients** over SSE+REST, WebSocket (STOMP), Kafka, or AMQP (RabbitMQ)
- **Manages sessions** — each client gets an isolated `ChatClient` instance and conversation memory, with pluggable storage (in-memory, JDBC, Redis, or Cassandra via Spring AI's `ChatMemoryRepository`)
- **Serializes requests per session** — concurrent messages queue up rather than racing
- **Streams responses in real time** — text chunks, tool call events, and question prompts are pushed as they happen
- **Bridges AskUserQuestionTool** — questions are pushed to the client; answers flow back and the agent resumes (requires [agent-utils](https://github.com/spring-ai-community/spring-ai-agent-utils) on classpath)
- **Evicts idle sessions** — configurable TTL-based cleanup prevents unbounded memory growth
- **Reports metrics and health** — Micrometer counters, gauges, and timers for sessions, requests, and question timeouts; Actuator health indicator for operational monitoring
- **Auto-configures** — add a starter dependency and provide a `ChatClient.Builder` bean

## Where This Fits

This is a companion project to [spring-ai-agent-utils](https://github.com/spring-ai-community/spring-ai-agent-utils). While agent-utils provides building blocks for creating agents (tools, skills, subagents, task management, interactive feedback), this project adds the serving infrastructure. It also works independently with any Spring AI `ChatClient`, even without agent-utils on the classpath.

This project is not a workflow engine, an orchestration framework, or a new agent abstraction. It handles sessions, streaming, and transport for agents that already work locally. [MCP](https://modelcontextprotocol.io/) tools work inside agents served by this project; [A2A](https://google.github.io/A2A/) enables inter-agent communication across services — both are complementary protocols, not alternatives.

## Deployment Models

The project supports two deployment models depending on how the enterprise's infrastructure is structured.

### Serve at the Edge

Clients connect directly. One service handles transport, sessions, and agent execution.

```
Browser/CLI/App -> SSE or WebSocket -> Serve -> ChatClient -> AI Model
```

This is what the demo apps use. Suitable for teams building a standalone agent service without existing messaging infrastructure.

### Serve Behind a Message Broker

The serve layer is a backend service in an event-driven pipeline. Clients never talk to it directly. Add the Kafka or AMQP starter and the agent processor consumes and produces messages — no web server needed.

```
End User -> [any protocol] -> Web App -> Kafka/RabbitMQ -> Serve -> Kafka/RabbitMQ -> Web App -> End User
```

The enterprise's web app produces an `AgentRequest` (session ID + message) to a topic or queue. The serve layer consumes it, routes to the right session, runs the agent, and produces `AgentEvent` messages back. The web app consumes those events and delivers them to the user however it wants.

Why enterprises use this model: in many organizations — particularly in financial services, healthcare, and other regulated industries — end-user applications are not permitted to call backend services like an AI agent directly. All communication flows through a message broker, where it can be audited, throttled, and governed centrally. Beyond compliance, many enterprises already have Kafka or RabbitMQ in place. Their order service, shipping service, and notification service all communicate through it. Their ops team monitors it, their deployment pipelines are built around it. The agent becomes another consumer/producer in infrastructure they already run.

See the [Kafka Agent Processor](examples/kafka/agent-processor) or the [AMQP Agent Processor](examples/amqp/agent-processor) for working examples.

## Use Cases

**Governed AI-assisted development** — An enterprise that wants to provide AI coding assistance to developers while controlling how code interacts with AI — particularly important in financial services, healthcare, and other regulated industries where code handles sensitive data and compliance requirements govern how AI tools are used. Instead of individual developers sending code to various external LLM providers from their laptops, all AI interactions are routed through a centralized agent service. The enterprise controls which LLM provider is used (including self-hosted models), enforces access policies on which repositories and tools are available, selectively exposes internal MCP servers (database schemas, deployment pipelines, internal APIs) as tools the agent can use, and logs every interaction for audit. Developers interact through a browser or a thin CLI client that the enterprise develops and deploys to developer machines; the LLM API calls happen server-side within the enterprise's network policies.

**Internal tools** — A DevOps assistant where developers ask *"Why did the staging deploy fail?"* and the agent calls `ci_logs` and `k8s_status` tools, streaming progress indicators while it works.

**Customer-facing chat** — A support agent with product-specific tools (account lookup, billing history). Each customer gets an isolated session with conversation memory.

**Interactive workflows** — An agent that helps users configure complex systems and asks clarifying questions mid-conversation using `AskUserQuestionTool`. The question is pushed to the browser, the user answers, and the agent resumes.

**Event-driven microservices** — A retailer's support platform where customer messages flow through Kafka or RabbitMQ. The agent calls order tracking and shipping tools, and response events flow back through the broker to the customer's app.

**Async batch processing** — An insurance company's claims department. An AI agent processes 200 claim documents overnight — reads each document, calls a `policy_lookup` tool to check coverage, calls a `fraud_signals` tool to flag inconsistencies, and writes a preliminary assessment. Adjusters review the next morning. No client is watching the stream, but session isolation still matters (each claim is its own conversation) and tool call observation matters for operational monitoring.

## Quick Start

### 1. Add the SSE starter

```xml
<dependency>
    <groupId>org.springaicommunity</groupId>
    <artifactId>spring-ai-agent-serve-starter-sse</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### 2. Define a `ChatClient.Builder` bean and tools

```java
@Bean
ChatClient.Builder agentChatClientBuilder(ChatModel chatModel) {
    return ChatClient.builder(chatModel)
        .defaultSystem("You are a helpful AI assistant.");
}

@Bean
List<ToolCallback> agentTools() {
    return Arrays.asList(ToolCallbacks.from(
        GrepTool.builder().build(), GlobTool.builder().build(),
        FileSystemTools.builder().build(), ShellTools.builder().build()));
}
```

Tools are declared as a separate `List<ToolCallback>` bean. The auto-configuration wraps them with `ObservableToolCallback` decorators so that `TOOL_CALL_STARTED`/`TOOL_CALL_COMPLETED` events are emitted to clients in real time.

### 3. Run the application

```bash
export ANTHROPIC_API_KEY=your-key-here
mvn spring-boot:run
```

### 4. Connect a client

**Open an SSE event stream:**
```bash
curl -N http://localhost:8080/api/agent/sessions/new/events
```

The server generates a session ID and sends it as the first event:
```
event: SESSION_CREATED
data: {"sessionId":"a1b2c3d4-...","type":"SESSION_CREATED","content":null,"metadata":{"sessionId":"a1b2c3d4-..."}}
```

**Send a message:**
```bash
curl -X POST http://localhost:8080/api/agent/sessions/a1b2c3d4-.../messages \
  -H "Content-Type: application/json" \
  -d '{"message": "What is Spring AI?"}'
```

**Events stream back on the SSE connection:**
```
event: RESPONSE_CHUNK
data: {"sessionId":"a1b2c3d4-...","type":"RESPONSE_CHUNK","content":"Spring AI is","metadata":{}}

event: RESPONSE_CHUNK
data: {"sessionId":"a1b2c3d4-...","type":"RESPONSE_CHUNK","content":" a framework for...","metadata":{}}

event: FINAL_RESPONSE
data: {"sessionId":"a1b2c3d4-...","type":"FINAL_RESPONSE","content":"Spring AI is a framework for...","metadata":{}}
```

In a browser, use the native `EventSource` API:
```javascript
const eventSource = new EventSource('/api/agent/sessions/new/events');
eventSource.addEventListener('RESPONSE_CHUNK', (e) => {
    const event = JSON.parse(e.data);
    appendText(event.content);
});
```

### Using WebSocket Instead

Swap the starter:

```xml
<dependency>
    <groupId>org.springaicommunity</groupId>
    <artifactId>spring-ai-agent-serve-starter-websocket</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Clients send JSON messages to `/app/agent` and subscribe to `/topic/agent/{sessionId}` for responses over STOMP. See the [WebSocket Chatbot Demo](examples/websocket/chatbot-demo).

### Using Kafka Instead

For event-driven architectures where the agent is a backend service behind a message broker:

```xml
<dependency>
    <groupId>org.springaicommunity</groupId>
    <artifactId>spring-ai-agent-serve-starter-kafka</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <type>pom</type>
</dependency>
```

The agent processor consumes `AgentRequest` JSON from a Kafka topic, processes through the agent, and produces `AgentEvent` JSON back. No web server needed. See the [Kafka Agent Processor](examples/kafka/agent-processor).

### Using AMQP (RabbitMQ) Instead

For enterprises running RabbitMQ:

```xml
<dependency>
    <groupId>org.springaicommunity</groupId>
    <artifactId>spring-ai-agent-serve-starter-amqp</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <type>pom</type>
</dependency>
```

Same pattern as Kafka — the agent processor consumes from RabbitMQ queues and produces events to a topic exchange. The `sessionId` is used as the routing key, allowing downstream consumers to receive events for specific sessions. See the [AMQP Agent Processor](examples/amqp/agent-processor).

## Examples

| Example | Description |
|---------|-------------|
| [SSE Chatbot Demo](examples/sse/chatbot-demo) | Browser-based chat UI over SSE (recommended starting point) |
| [SSE CLI Client](examples/sse/cli-client) | Terminal client connecting over SSE |
| [WebSocket Chatbot Demo](examples/websocket/chatbot-demo) | Browser-based chat UI over WebSocket/STOMP |
| [WebSocket CLI Client](examples/websocket/cli-client) | Terminal client connecting over STOMP |
| [Kafka Agent Processor](examples/kafka/agent-processor) | Backend agent service consuming/producing via Kafka |
| [Kafka CLI Client](examples/kafka/cli-client) | Terminal client interacting with the agent over Kafka |
| [AMQP Agent Processor](examples/amqp/agent-processor) | Backend agent service consuming/producing via RabbitMQ |
| [AMQP CLI Client](examples/amqp/cli-client) | Terminal client interacting with the agent over RabbitMQ |

## SSE Endpoints

SSE is inherently half-duplex (server-to-client only), so client-to-server communication uses standard REST POST endpoints. This is simpler than WebSocket/STOMP and works through proxies and load balancers without special configuration.

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/agent/sessions/{sessionId}/events` | Opens an SSE event stream. Use `new` as the session ID to auto-generate one. The server sends a `SESSION_CREATED` event with the assigned ID. |
| `POST` | `/api/agent/sessions/{sessionId}/messages` | Sends a user message to the agent. The response streams back on the SSE connection. Returns `202 Accepted`. |
| `POST` | `/api/agent/sessions/{sessionId}/answers` | Resolves a pending question from `AskUserQuestionTool`. Returns `204 No Content` on success, `404 Not Found` if the session is unknown. |

## WebSocket Endpoints

WebSocket uses STOMP (Simple Text Oriented Messaging Protocol) over SockJS. Clients connect to the STOMP endpoint (default `/ws`), send messages to application destinations, and subscribe to topics for responses.

| Direction | Destination | Payload | Description |
|-----------|-------------|---------|-------------|
| Client -> Server | `/app/agent` | `AgentRequest` (`sessionId`, `message`) | Sends a user message. If `sessionId` is null or blank, a UUID is generated. |
| Client -> Server | `/app/agent/answer` | `QuestionAnswer` (`sessionId`, `questionId`, `answers`) | Resolves a pending question from `AskUserQuestionTool`. |
| Server -> Client | `/topic/agent/{sessionId}` | `AgentEvent` | Subscribe here to receive all events for a session. |

**Browser example with SockJS + STOMP.js:**
```javascript
const socket = new SockJS('/ws');
const stompClient = Stomp.over(socket);
const sessionId = crypto.randomUUID();

stompClient.connect({}, () => {
    stompClient.subscribe('/topic/agent/' + sessionId, (msg) => {
        const event = JSON.parse(msg.body);
        switch (event.type) {
            case 'RESPONSE_CHUNK':    // append streaming text
            case 'TOOL_CALL_STARTED': // show "Calling GrepTool..."
            case 'TOOL_CALL_COMPLETED': // update to "Used GrepTool"
            case 'QUESTION_REQUIRED': // render question card
            case 'FINAL_RESPONSE':    // mark response complete
            case 'ERROR':             // show error
        }
    });

    stompClient.send('/app/agent', {},
        JSON.stringify({ sessionId: sessionId, message: 'Hello' }));
});
```

## Kafka Topics

The Kafka transport uses three topics for communication between the upstream application and the agent processor. All topic names are configurable.

| Topic | Direction | Message Type | Description |
|-------|-----------|--------------|-------------|
| `agent-requests` | Inbound | `AgentRequest` JSON | User messages from the upstream app. The `sessionId` field determines which session handles the request. |
| `agent-answers` | Inbound | `QuestionAnswer` JSON | Answers to questions posed by the agent's `AskUserQuestionTool`. |
| `agent-events` | Outbound | `AgentEvent` JSON | All agent events: streaming chunks, tool call lifecycle, questions, final responses, and errors. |

The `sessionId` is used as the Kafka message key for all messages. This provides partition ordering — all messages for the same session are delivered to the same partition in order, and the downstream consumer can route responses back to the right user by key.

**Produce a request:**
```bash
echo '{"sessionId":"session-1","message":"What is Spring AI?"}' | \
  kafka-console-producer.sh --bootstrap-server localhost:9092 --topic agent-requests
```

**Consume events:**
```bash
kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic agent-events
```

## AMQP Queues and Exchange

The AMQP transport uses two queues for inbound messages and a topic exchange for outbound events. All names are configurable. The AMQP topology (queues and exchange) is declared automatically on startup.

| Resource | Type | Direction | Message Type | Description |
|----------|------|-----------|--------------|-------------|
| `agent-requests` | Queue (durable) | Inbound | `AgentRequest` JSON | User messages from the upstream app. |
| `agent-answers` | Queue (durable) | Inbound | `QuestionAnswer` JSON | Answers to questions posed by the agent's `AskUserQuestionTool`. |
| `agent-events` | Topic exchange | Outbound | `AgentEvent` JSON | All agent events. The `sessionId` is used as the routing key. |

The `sessionId` as the routing key allows downstream consumers to bind queues with specific routing keys to receive events for individual sessions — RabbitMQ does the filtering server-side, unlike Kafka where consumers filter by key client-side.

## Event Types

The serve layer emits typed events so clients can render agent activity in real time. All events share the same `AgentEvent` record structure:

```java
public record AgentEvent(
    String sessionId,
    String type,
    String content,              // text for RESPONSE_CHUNK, FINAL_RESPONSE, ERROR
    Map<String, Object> metadata // structured data for TOOL_CALL_*, QUESTION_REQUIRED
) { }
```

| Type | Content | Metadata | Purpose |
|------|---------|----------|---------|
| `SESSION_CREATED` | — | `sessionId` | SSE only: confirms new session with assigned ID |
| `RESPONSE_CHUNK` | text fragment | — | A token or partial response (streaming text) |
| `TOOL_CALL_STARTED` | — | `toolName` | Agent is invoking a tool |
| `TOOL_CALL_COMPLETED` | — | `toolName` | Tool returned a result |
| `QUESTION_REQUIRED` | — | `questionId`, `questions` | Agent needs user input (includes options) |
| `FINAL_RESPONSE` | full accumulated text | — | Complete response (end of stream) |
| `ERROR` | error message | — | Something went wrong |

The `metadata` map uses `Map<String, Object>` rather than `Map<String, String>` because the `questions` value in `QUESTION_REQUIRED` is a complex object (list of questions with options) that Jackson serializes to a JSON array.

## How Sessions Work

```
Client connects with sessionId
  |
  v
AgentSessionManager.getOrCreate(sessionId)
  |
  +-- First time? Create new AgentSession:
  |     - Clone the ChatClient.Builder (for isolation)
  |     - Create a new MessageWindowChatMemory for this session
  |     - Bind MessageChatMemoryAdvisor with conversationId
  |     - Wrap tools with ObservableToolCallback for event emission
  |     - Wire AskUserQuestionTool with ServeQuestionHandler (if agent-utils present)
  |     - Build the ChatClient
  |     - Store in session map
  |
  +-- Returning? Retrieve existing AgentSession
        - Same ChatClient, same memory
        - Conversation continues
```

Each session gets its own single-threaded executor. Concurrent requests to the same session queue up rather than racing. This prevents memory corruption, interleaved tool executions, and unpredictable conversation state.

```
Without serial execution:                With serial execution:

User sends "create file A"               User sends "create file A"
User sends "read file A"                 User sends "read file A"
     |          |                              |          |
     v          v                              v          |  (queued)
  Thread-1   Thread-2                       Thread-1      |
  creating   reading A                      creating A    |
  file A     FILE NOT FOUND!                   done        v
                                            Thread-1
                                            reading A
                                            success!
```

Idle sessions are automatically evicted after the configured TTL (default: 30 minutes). Any interaction with a session (sending a message, answering a question) refreshes the TTL. The eviction scheduler runs periodically (default: every 60 seconds) and cleans up sessions that have been idle longer than the TTL. `InMemoryAgentSessionManager` implements `AutoCloseable` — on shutdown, it stops the scheduler and destroys all active sessions.

### Persistent Session Memory

By default, conversation memory is stored in-memory and lost on restart. For production deployments where conversation history should survive restarts or be shared across nodes, the serve layer integrates with Spring AI's pluggable `ChatMemoryRepository` abstraction.

Add a Spring AI memory repository starter to your classpath and conversation history is automatically persisted. No serve layer configuration is needed — Spring AI's auto-configuration creates the repository bean, and the serve layer picks it up.

**JDBC** (PostgreSQL, MySQL, H2, SQLite, MariaDB, Oracle, SQL Server):

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-chat-memory-repository-jdbc</artifactId>
</dependency>
```

**Redis:**

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-chat-memory-redis</artifactId>
</dependency>
```

**Cassandra:**

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-chat-memory-repository-cassandra</artifactId>
</dependency>
```

Configure the underlying data store (datasource, Redis connection, or Cassandra session) through the standard Spring Boot properties. The serve layer shares the same `ChatMemoryRepository` across all sessions — each session uses its own `conversationId` as the namespace within the repository.

## Architecture

```
Clients                          Serve                           Agent Stack
---------                        -----                           -----------

Browser --+                  +- Transport --+                  +- ChatClient
           |                  |  SSE+REST     |                  |   .prompt()
CLI -------+-- connect ------>|  WebSocket    +-- delegate ----->|   .stream()
           |                  |  Kafka        |                  |
           |                  |  AMQP         |                  |
App ------+                  +------+-------+                  +- agent-utils
                                    |                          |   tools, skills
                             AgentSession                      |   subagents
                             (per-client state,                |   advisors
                              memory binding,                  |
                              request queuing)                 +- Memory
                                                               |   (per-session)
                                                               |
                                                               +- AI Model
                                                                   (Claude, OpenAI, etc.)
```

All agent behavior stays inside `ChatClient` and agent-utils. This project handles transport, sessions, and protocol bridging.

### Layer Responsibilities

| Layer | Responsibility | Does NOT do |
|-------|---------------|-------------|
| **Transport** (SSE, WebSocket, Kafka, or AMQP) | Accept connections/messages, frame events, push responses | Agent logic, tool execution |
| **AgentSession** | Session lifecycle, memory binding, request queuing | Transport details |
| **QuestionHandler bridge** | Route AskUserQuestionTool to/from remote clients | Question logic (that's the tool's job) |
| **ChatClient + agent-utils** | All agent behavior — tools, skills, orchestration | Transport, sessions |

### Transport

Each application uses a **single transport**. Pick a starter:

| Starter | Transport | Client Protocol | Use Case |
|---------|-----------|----------------|----------|
| `spring-ai-agent-serve-starter-sse` | SSE + REST | `EventSource` + `fetch()` | Browser/CLI clients connecting directly |
| `spring-ai-agent-serve-starter-websocket` | WebSocket (STOMP) | SockJS + STOMP | Browser clients needing full-duplex |
| `spring-ai-agent-serve-starter-kafka` | Kafka topics | Kafka producer/consumer | Backend agent in Kafka-based pipeline |
| `spring-ai-agent-serve-starter-amqp` | RabbitMQ queues + exchange | AMQP producer/consumer | Backend agent in RabbitMQ-based pipeline |

SSE is the recommended default for client-facing applications. It works through proxies and load balancers without special configuration, uses the browser's native `EventSource` API, and follows the same pattern used by most AI API providers. Kafka and AMQP are the recommended choices for backend agent services in event-driven architectures where the agent is a consumer/producer behind a message broker.

## Configuration

### Core Properties

These apply regardless of which transport you choose.

```yaml
spring:
  ai:
    agent:
      serve:
        enabled: true                    # default
        max-messages: 500                # messages kept in session memory
        session:
          ttl: 30m                       # idle session time-to-live
          eviction-interval: 60s         # how often the evictor runs
        question:
          timeout-minutes: 5             # how long to wait for user answers
```

### SSE Properties

```yaml
spring:
  ai:
    agent:
      serve:
        sse:
          emitter-timeout-ms: 1800000    # SSE connection timeout (30 min)
          allowed-origins:
            - "http://localhost:*"       # CORS; restrict in production
```

### WebSocket Properties

```yaml
spring:
  ai:
    agent:
      serve:
        websocket:
          endpoint: /ws                  # STOMP endpoint path
          allowed-origins:
            - "http://localhost:*"       # CORS; restrict in production
```

### Kafka Properties

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092     # standard Spring Boot Kafka property
  ai:
    agent:
      serve:
        kafka:
          requests-topic: agent-requests   # inbound: user messages
          answers-topic: agent-answers     # inbound: question answers
          events-topic: agent-events       # outbound: agent events
          consumer-group: agent-serve      # consumer group for serve listeners
```

The Kafka transport reuses Spring Boot's standard `spring.kafka.*` properties for broker configuration. The serve-specific properties under `spring.ai.agent.serve.kafka.*` configure topic names and the consumer group.

### AMQP Properties

```yaml
spring:
  rabbitmq:
    host: localhost                        # standard Spring Boot RabbitMQ property
    port: 5672
  ai:
    agent:
      serve:
        amqp:
          request-queue: agent-requests    # inbound: user messages
          answer-queue: agent-answers      # inbound: question answers
          events-exchange: agent-events    # outbound: topic exchange for agent events
```

The AMQP transport reuses Spring Boot's standard `spring.rabbitmq.*` properties for broker configuration. The serve-specific properties under `spring.ai.agent.serve.amqp.*` configure queue names and the events exchange.

Each transport module owns its own `@ConfigurationProperties` class (`AgentSseProperties`, `AgentWebSocketProperties`, `AgentKafkaProperties`, `AgentAmqpProperties`), so there is no configuration conflict between modules.

## Observability

When Micrometer is on the classpath, the serve layer automatically registers metrics for monitoring session activity, request processing, tool execution, and question handling.

### Metrics

| Metric | Type | Description |
|--------|------|-------------|
| `agent.serve.sessions.active` | Gauge | Number of currently active sessions |
| `agent.serve.sessions.created` | Counter | Total sessions created since startup |
| `agent.serve.sessions.evicted` | Counter | Sessions evicted due to idle TTL |
| `agent.serve.requests` | Counter | Total streaming requests processed |
| `agent.serve.request.duration` | Timer | Time from request submission to stream completion |
| `agent.serve.questions.timed.out` | Counter | Questions that expired before the user answered |

Tool call metrics (execution count and duration) are provided by Spring AI's built-in Micrometer observation pipeline via `DefaultToolCallingManager` and are available automatically when Micrometer is on the classpath.

These metrics are available through any Micrometer-supported monitoring system — Prometheus, Grafana, Datadog, CloudWatch, and others. No additional configuration is needed beyond having Micrometer on the classpath.

### Health Indicator

When Spring Boot Actuator is on the classpath, a health indicator reports the serve layer's status:

```
GET /actuator/health
```

```json
{
  "status": "UP",
  "components": {
    "agentServe": {
      "status": "UP",
      "details": {
        "activeSessions": 12
      }
    }
  }
}
```

### Opting In

Observability is automatic when the dependencies are present. The SSE starter, WebSocket starter, and any Spring Boot web application typically include Actuator and Micrometer already. For Kafka or AMQP agent processors (non-web), add the Actuator starter:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

Metrics and the health indicator are registered via `@ConditionalOnClass` — if Micrometer or Actuator is not on the classpath, no metrics code runs and there is no impact on the application.

## Customization

Every auto-configured bean uses `@ConditionalOnMissingBean`. Define your own and the default backs off:

```java
@Bean
AgentSessionManager agentSessionManager(ChatClient.Builder builder) {
    return new MyRedisSessionManager(builder);
}

@Bean
AgentEventSender agentEventSender() {
    return new MyCustomEventSender();
}
```

The project separates what's hard to build from what's app-specific:

| You'll probably keep | You might replace |
|---------------------|-------------------|
| Session lifecycle — creation via `clone()`, serial execution, eviction | Transport — gRPC, GraphQL subscriptions |
| Streaming event framing — `ChatClient.stream()` into typed `AgentEvent` streams | Session storage — Redis, JDBC |
| Question bridge — `CompletableFuture` coordination, timeouts, thread safety | Authentication, error handling |
| Event type contract — `RESPONSE_CHUNK`, `TOOL_CALL_STARTED`, `QUESTION_REQUIRED` | |

The callback-based `Consumer<AgentEvent>` API means any transport can receive events:

```java
// gRPC
session.submitStream(toAgentRequest(req), event -> observer.onNext(toGrpcEvent(event)));

// Custom transport
session.submitStream(request, event -> myTransport.send(sessionId, event));
```

## Project Structure

```
spring-ai-agent-serve/
+-- spring-ai-agent-serve-core/              # Sessions, events, tool observability, question bridge, metrics
+-- spring-ai-agent-serve-sse/               # SSE + REST transport
+-- spring-ai-agent-serve-websocket/         # WebSocket (STOMP) transport
+-- spring-ai-agent-serve-kafka/             # Kafka consumer/producer transport
+-- spring-ai-agent-serve-amqp/             # AMQP (RabbitMQ) consumer/producer transport
+-- spring-ai-agent-serve-starter-sse/       # Spring Boot starter for SSE
+-- spring-ai-agent-serve-starter-websocket/ # Spring Boot starter for WebSocket
+-- spring-ai-agent-serve-starter-kafka/     # Spring Boot starter for Kafka
+-- spring-ai-agent-serve-starter-amqp/     # Spring Boot starter for AMQP (RabbitMQ)
+-- examples/
    +-- sse/
    |   +-- chatbot-demo/                   # Browser chat UI over SSE
    |   +-- cli-client/                     # Terminal client over SSE
    +-- websocket/
    |   +-- chatbot-demo/                   # Browser chat UI over WebSocket
    |   +-- cli-client/                     # Terminal client over STOMP
    +-- kafka/
    |   +-- agent-processor/               # Backend agent service over Kafka
    |   +-- cli-client/                     # Terminal client over Kafka
    +-- amqp/
        +-- agent-processor/               # Backend agent service over RabbitMQ
        +-- cli-client/                     # Terminal client over RabbitMQ
```

Each transport module is self-contained with its own auto-configuration and properties. The core module has zero transport dependencies.

## Current Status

Early release (0.1.0-SNAPSHOT). Four transports available: SSE+REST (recommended for client-facing), WebSocket (STOMP), Kafka, and AMQP (RabbitMQ). Pluggable session memory via Spring AI's `ChatMemoryRepository` (JDBC, Redis, Cassandra). Observability via Micrometer metrics and Actuator health indicator.

**Planned:**
- Request cancellation
- Security integration points

## Requirements

- Java 17+
- Spring Boot 4.0.0+
- Spring AI 2.0.0-M2+

## License

Apache License 2.0
