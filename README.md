# Spring AI Agent Serve

An embeddable Spring Boot library that adds session management, event framing, and multi-transport delivery to your Spring AI agents — so multiple users can interact with them over the network. You focus on the agent's domain logic — tools, skills, and system prompt. The serve layer handles sessions, streaming, and transport. Add a starter dependency, provide a `ChatClient.Builder` bean, and your agent is served.

- **Session management** — each user gets an isolated `ChatClient` with its own conversation memory, and concurrent requests are serialized to prevent state corruption.
- **Event framing** — Spring AI's raw `ChatClient.stream()` produces `Flux<ChatResponse>` chunks; the serve layer translates these into typed `AgentEvent` messages (`RESPONSE_CHUNK`, `TOOL_CALL_STARTED`, `TOOL_CALL_COMPLETED`, `QUESTION_REQUIRED`, `FINAL_RESPONSE`, `ERROR`) that clients can render directly.
- **Human-in-the-loop** — when an agent needs user input mid-execution (e.g., clarifying a question before proceeding), the serve layer bridges this over the network: the question is pushed to the client, the agent thread pauses, and processing resumes when the answer arrives.
- **Multi-transport delivery** — those events are delivered over SSE, WebSocket (STOMP), Kafka, or AMQP (RabbitMQ), with a single transport per application.

```
                    spring-ai-agent-serve                       Spring AI
                 ┌──────────────────────────┐            ┌──────────────────────┐
Browser ──┐      │  Transport               │            │  ChatClient          │
          │      │  (SSE, WebSocket,        │  delegate  │   .prompt()          │
CLI ──────┼─────>│   Kafka, AMQP)           ├───────────>│   .stream()          │
          │      │                          │            │                      │
App ──────┘      │  AgentSession            │            │  Tools, Memory,      │
                 │  (isolation, memory,     │            │  Advisors            │
                 │   serial execution,      │            │                      │
                 │   event framing)         │            │  AI Model            │
                 └──────────────────────────┘            │  (Claude, OpenAI...) │
                                                         └──────────────────────┘
```

## The Problem

A Spring AI agent is a `ChatClient` with tools. Deploying it behind a REST controller works — until multiple users start using it. Here's what happens:

**Conversations bleed across users.** Two support agents hit the endpoint at the same time. Both conversations go into the same `ChatMemory`. User A asks about order 789, User B asks about order 456. User A says "cancel it" — the agent cancels order 456, because that's the most recent one in the shared history. You add `conversationId` to isolate memory per user. That's one problem solved.

**Concurrent requests corrupt state.** A user double-clicks "send." Two requests with the same `conversationId` hit `chatClient.prompt()` simultaneously. Both load the same history, both execute tools, both save — one overwrites the other. A refund gets processed twice. You add a lock per session. That's two problems solved.

**The UI freezes during tool calls.** The agent calls a shipping API that takes 8 seconds. Your REST endpoint blocks — the user sees nothing. You switch to SSE streaming, but when tools run, the stream goes silent with no indication of what's happening. You wrap each tool with a decorator that emits "calling shipping_lookup..." events. That's three problems solved.

**The agent needs to ask the user a question.** "This refund is over $500 — escalate or process directly?" The `AskUserQuestionTool` exists, but it reads from `System.in` — useless in a browser. You build a `CompletableFuture` bridge: push the question as an SSE event, block the agent thread, add a new `/answer` endpoint, complete the future when the answer arrives. That's four problems solved.

**Sessions accumulate.** Users close browser tabs without disconnecting. Hundreds of stale sessions pile up. You add a scheduled evictor that tracks last activity and cleans up idle sessions. That's five problems solved.

You now have 600 lines of plumbing — session map, per-session locks, SSE streaming pipeline, tool call decorators, question-answer bridge, session evictor — that have nothing to do with your agent's domain logic. And the billing team just built the same thing with different variable names.

Spring AI provides the primitives for all of this — `ChatMemory` with `conversationId`, `ChatMemoryRepository` for pluggable persistence, `ChatClient.Builder.clone()` for isolated instances. This project composes those primitives into a reusable session and transport layer so teams don't have to build the plumbing themselves.

**When you need this library:** Multiple users interacting with the same agent over the network, each with their own conversation history. Streaming responses in real time. Agents that ask users questions mid-execution. Delivering agent events over SSE, WebSocket, Kafka, or AMQP.

**When you don't:** A single-user application, a batch job, or a simple request/response endpoint. In those cases, `ChatClient` with a `conversationId` parameter is all you need.

## Landscape

The serving layer — session management, streaming event delivery, transport, and interactive feedback — is a distinct concern from agent construction. The industry has recognized this, and several approaches exist.

### Managed Platforms

Cloud providers offer comprehensive managed platforms for running agents in production:

- [**AWS Bedrock AgentCore**](https://aws.amazon.com/bedrock/agentcore/) — managed runtimes, distributed memory stores (short-term and long-term), an agent gateway with MCP and A2A protocol support, identity management, policy enforcement, and observability. Supports agents built with multiple frameworks.

- [**Google Vertex AI Agent Engine**](https://cloud.google.com/vertex-ai/generative-ai/docs/agent-engine/overview) — managed deployment with session management, a memory bank for cross-session personalization, code execution sandboxing, content safety, and built-in evaluation.

- [**Microsoft Foundry Agent Service**](https://learn.microsoft.com/en-us/azure/foundry/agents/overview) — managed agent hosting with conversation management, tool orchestration, content safety, identity integration, and multi-agent workflow orchestration.

- [**LangGraph Platform**](https://langchain-ai.github.io/langgraph/concepts/langgraph_platform/) — durable execution with checkpointing, multiple streaming modes, human-in-the-loop endpoints, and background task queues. Python-only.

These platforms provide far more than session management — managed infrastructure, auto-scaling, security, and operational tooling. They are the right choice when you need fully operated agent infrastructure.

### Agent Frameworks

Agent frameworks on the JVM — [Spring AI](https://docs.spring.io/spring-ai/reference/), [LangChain4j](https://docs.langchain4j.dev/), [Semantic Kernel](https://learn.microsoft.com/en-us/semantic-kernel/overview/), [Embabel](https://github.com/embabel/embabel-agent), and [Koog](https://github.com/JetBrains/koog) — provide excellent tooling for building agents (models, tools, memory, orchestration), but do not include a reusable serving layer for sessions and transport.

### Where This Project Fits

This project is not a managed platform. It is an embeddable Spring Boot library that addresses a narrower concern: serving a Spring AI `ChatClient` to multiple users over the network with session isolation, event framing, and transport abstraction. It handles sessions, serialization, streaming events, and interactive feedback bridging — the plumbing between a `ChatClient` and remote clients. Authentication, content safety, scaling, and operational infrastructure remain the application's responsibility.

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

Each of these requires what a plain `ChatClient` call doesn't provide: multiple users with isolated sessions, streaming events over the network, or interactive feedback bridging.

**Governed AI-assisted development** — Multiple developers interact with a centralized AI coding agent through a browser or CLI client. Each developer gets an isolated session. The enterprise controls which LLM provider is used, which tools are available, and can log every interaction. The serve layer provides the multi-user session infrastructure; the LLM API calls happen server-side within the enterprise's network policies.

**Customer-facing chat** — A support agent with product-specific tools (account lookup, billing history). Each customer gets an isolated session with conversation memory. Streaming events show real-time progress as the agent calls tools.

**Interactive workflows** — An agent that helps users configure complex systems and asks clarifying questions mid-conversation. The question is pushed to the browser, the user answers, and the agent resumes — coordinated across the network via the serve layer's human-in-the-loop bridge.

**Event-driven microservices** — A retailer's support platform where customer messages flow through Kafka or RabbitMQ. The agent calls order tracking and shipping tools, and response events flow back through the broker to the customer's app. Each customer's conversation is isolated via session management.

## Quick Start

This is the entire application. No controllers, no session management code, no streaming infrastructure — just the agent's capabilities. The serve layer auto-configures everything else.

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

<details>
<summary><h2>SSE Endpoints</h2></summary>

SSE is inherently half-duplex (server-to-client only), so client-to-server communication uses standard REST POST endpoints. This is simpler than WebSocket/STOMP and works through proxies and load balancers without special configuration.

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/agent/sessions/{sessionId}/events` | Opens an SSE event stream. Use `new` as the session ID to auto-generate one. The server sends a `SESSION_CREATED` event with the assigned ID. |
| `POST` | `/api/agent/sessions/{sessionId}/messages` | Sends a user message to the agent. The response streams back on the SSE connection. Returns `202 Accepted`. |
| `POST` | `/api/agent/sessions/{sessionId}/answers` | Resolves a pending question from `AskUserQuestionTool`. Returns `204 No Content` on success, `404 Not Found` if the session is unknown. |

</details>

<details>
<summary><h2>WebSocket Endpoints</h2></summary>

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

</details>

<details>
<summary><h2>Kafka Topics</h2></summary>

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

</details>

<details>
<summary><h2>AMQP Queues and Exchange</h2></summary>

The AMQP transport uses two queues for inbound messages and a topic exchange for outbound events. All names are configurable. The AMQP topology (queues and exchange) is declared automatically on startup.

| Resource | Type | Direction | Message Type | Description |
|----------|------|-----------|--------------|-------------|
| `agent-requests` | Queue (durable) | Inbound | `AgentRequest` JSON | User messages from the upstream app. |
| `agent-answers` | Queue (durable) | Inbound | `QuestionAnswer` JSON | Answers to questions posed by the agent's `AskUserQuestionTool`. |
| `agent-events` | Topic exchange | Outbound | `AgentEvent` JSON | All agent events. The `sessionId` is used as the routing key. |

The `sessionId` as the routing key allows downstream consumers to bind queues with specific routing keys to receive events for individual sessions — RabbitMQ does the filtering server-side, unlike Kafka where consumers filter by key client-side.

</details>

<details>
<summary><h2>Event Types</h2></summary>

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

</details>

<details>
<summary><h2>How Sessions Work</h2></summary>

```
Client connects with sessionId
  |
  v
AgentSessionManager.getOrCreate(sessionId)
  |
  +-- First time? Create new AgentSession:
  |     - Clone the ChatClient.Builder (lightweight — shares the underlying ChatModel)
  |     - Create per-session ChatMemory (backed by pluggable ChatMemoryRepository)
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

### Scalability

Each session creates a per-session `ChatClient` via `ChatClient.Builder.clone()`. This is lightweight — the clone copies the default request configuration (system prompt, tool references, advisor references) but shares the underlying `ChatModel`, which holds the HTTP connection pool to the LLM provider. A thousand sessions do not create a thousand HTTP clients — they share one.

The primary resource cost per session is the single-threaded executor. Each session gets its own thread to serialize requests. At scale, the thread count is bounded by session eviction (idle sessions are destroyed after the configured TTL), so the concurrent thread count reflects active users, not total users over time.

For higher scale, the executor can be switched to virtual threads (Java 21+), which removes the platform thread overhead entirely. This is a one-line change in `AgentSession` and does not affect the rest of the architecture.

</details>

<details>
<summary><h2>Architecture</h2></summary>

All agent behavior stays inside `ChatClient` and Spring AI (see [diagram above](#spring-ai-agent-serve)). This project handles transport, sessions, and protocol bridging.

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

</details>

<details>
<summary><h2>Configuration</h2></summary>

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

</details>

<details>
<summary><h2>Observability</h2></summary>

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

</details>

<details>
<summary><h2>Customization</h2></summary>

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

</details>

## Project Structure

The library is organized as a core module plus pluggable transport modules, each with a corresponding Spring Boot starter:

| Module | Purpose |
|--------|---------|
| `spring-ai-agent-serve-core` | Sessions, events, tool observability, question bridge, metrics |
| `spring-ai-agent-serve-sse` | SSE + REST transport |
| `spring-ai-agent-serve-websocket` | WebSocket (STOMP) transport |
| `spring-ai-agent-serve-kafka` | Kafka consumer/producer transport |
| `spring-ai-agent-serve-amqp` | AMQP (RabbitMQ) consumer/producer transport |
| `spring-ai-agent-serve-starter-*` | Spring Boot starters (one per transport) |

Each transport module is self-contained with its own auto-configuration and properties. The core module has zero transport dependencies.

## Current Status

Four transports available: SSE+REST (recommended for client-facing), WebSocket (STOMP), Kafka, and AMQP (RabbitMQ). Pluggable session memory via Spring AI's `ChatMemoryRepository` (JDBC, Redis, Cassandra). Observability via Micrometer metrics and Actuator health indicator.

## Requirements

- Java 17+
- Spring Boot 4.0.0+
- Spring AI 2.0.0-M2+
