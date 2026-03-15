# Spring AI Agent Serve

Serving infrastructure for Spring AI agents — sessions, streaming, tool call events, and transport for multi-user applications.

## The Problem

A Spring AI agent is a `ChatClient` with tools. It works well in a single-process application. The challenge comes when multiple users need to interact with that agent over the network — through a browser, CLI, or mobile app.

For plain chat, a REST controller returning `Flux<String>` is enough. But agents are different:

- **Tool calls take time.** An agent calling `GrepTool`, then `FileSystemTools`, then generating a response can take 30+ seconds. Without event framing, the UI shows a frozen spinner the entire time.
- **Multiple users need isolation.** Two users hitting the same endpoint shouldn't see each other's conversation history or interfere with each other's tool executions.
- **Concurrent requests corrupt state.** Two messages to the same session at once can interleave tool executions and corrupt conversation memory.
- **Agents ask questions back.** When an agent calls `AskUserQuestionTool` mid-stream, the user needs to answer before the agent can continue. Coordinating this over a network — push the question to the browser, block the agent thread, wait for the answer, resume — is tricky to get right.

If you have three teams building Spring AI agents (sales, supply chain, billing), each team ends up writing its own session isolation, request serialization, streaming event framing, and transport plumbing. Three slightly different implementations, three different threading approaches.

This project packages those common concerns so teams can focus on their agent's domain logic instead.

## What It Does

- **Exposes a ChatClient to remote clients** over SSE+REST or WebSocket (STOMP)
- **Manages sessions** — each client gets an isolated `ChatClient` instance and conversation memory
- **Serializes requests per session** — concurrent messages queue up rather than racing
- **Streams responses in real time** — text chunks, tool call events, and question prompts are pushed as they happen
- **Bridges AskUserQuestionTool** — questions are pushed to the client; answers flow back and the agent resumes (requires [agent-utils](https://github.com/spring-ai-community/spring-ai-agent-utils) on classpath)
- **Evicts idle sessions** — configurable TTL-based cleanup prevents unbounded memory growth
- **Auto-configures** — add a starter dependency and provide a `ChatClient.Builder` bean

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

### 3. Connect a client

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

## Examples

| Example | Description |
|---------|-------------|
| [SSE Chatbot Demo](examples/sse/chatbot-demo) | Browser-based chat UI over SSE (recommended starting point) |
| [SSE CLI Client](examples/sse/cli-client) | Terminal client connecting over SSE |
| [WebSocket Chatbot Demo](examples/websocket/chatbot-demo) | Browser-based chat UI over WebSocket/STOMP |
| [WebSocket CLI Client](examples/websocket/cli-client) | Terminal client connecting over STOMP |
| [Programmatic Demo](examples/programmatic-demo) | Standalone app using the core library directly, no transport |

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

## Architecture

```
Clients                          Serve                           Agent Stack
---------                        -----                           -----------

Browser --+                  +- Transport --+                  +- ChatClient
           |                  |  (SSE+REST,   |                  |   .prompt()
CLI -------+-- connect ------>|   WebSocket,  +-- delegate ----->|   .stream()
           |                  |   or direct)  |                  |
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
| **Transport** (SSE or WebSocket) | Accept connections, frame messages, push events | Agent logic, tool execution |
| **AgentSession** | Session lifecycle, memory binding, request queuing | Transport details |
| **QuestionHandler bridge** | Route AskUserQuestionTool to/from remote clients | Question logic (that's the tool's job) |
| **ChatClient + agent-utils** | All agent behavior — tools, skills, orchestration | Transport, sessions |

### Transport

Each application uses a **single transport**. Pick a starter:

| Starter | Transport | Client Protocol |
|---------|-----------|----------------|
| `spring-ai-agent-serve-starter-sse` | SSE + REST | `EventSource` + `fetch()` |
| `spring-ai-agent-serve-starter-websocket` | WebSocket (STOMP) | SockJS + STOMP |

SSE is the recommended default. It works through proxies and load balancers without special configuration, uses the browser's native `EventSource` API, and follows the same pattern used by most AI API providers.

### Deployment Models

The project supports two deployment models depending on how the enterprise's infrastructure is structured.

#### Model 1: Serve at the Edge

Clients connect directly. One service handles transport, sessions, and agent execution.

```
Browser/CLI/App -> SSE or WebSocket -> Serve -> ChatClient -> AI Model
```

This is what the demo apps use. Suitable for teams building a standalone agent service without existing messaging infrastructure.

#### Model 2: Serve Behind a Message Broker

The serve layer is a backend service in an event-driven pipeline. Clients never talk to it directly.

```
End User -> [any protocol] -> Web App -> Kafka/RabbitMQ -> Serve -> Kafka/RabbitMQ -> Web App -> End User
```

The enterprise's web app produces an `AgentRequest` (session ID + message) to a topic/queue. The serve layer consumes it, routes to the right session, runs the agent, and produces `AgentEvent` messages back. The web app consumes those events and delivers them to the user however it wants.

Why enterprises use this model: they already have Kafka or RabbitMQ, their services communicate through it, their ops team monitors it, and their deployment pipelines are built around it. The agent becomes another consumer/producer in infrastructure they already run.

### Use Cases

**Internal tools** — A DevOps assistant where developers ask *"Why did the staging deploy fail?"* and the agent calls `ci_logs` and `k8s_status` tools, streaming progress indicators while it works.

**Customer-facing chat** — A support agent with product-specific tools (account lookup, billing history). Each customer gets an isolated session with conversation memory.

**Interactive workflows** — An agent that helps users configure complex systems and asks clarifying questions mid-conversation using `AskUserQuestionTool`. The question is pushed to the browser, the user answers, and the agent resumes.

**Event-driven microservices** — A retailer's support platform where customer messages flow through Kafka. The agent calls order tracking and shipping tools, and response events flow back through Kafka to the customer's app.

**Audit/compliance** — An agent that sends events to both SSE (real-time to the user) and Kafka (immutable audit trail) simultaneously through a `CompositeEventSender`.

**Async batch processing** — An insurance company's claims department. An AI agent processes 200 claim documents overnight — reads each document, calls a `policy_lookup` tool to check coverage, calls a `fraud_signals` tool to flag inconsistencies, and writes a preliminary assessment. Adjusters review the next morning. No client is watching the stream, but session isolation still matters (each claim is its own conversation) and tool call observation matters for operational monitoring.

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

Each transport module owns its own `@ConfigurationProperties` class (`AgentSseProperties`, `AgentWebSocketProperties`), so there is no configuration conflict between modules.

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
| Session lifecycle — creation via `clone()`, serial execution, eviction | Transport — gRPC, GraphQL subscriptions, Kafka |
| Streaming event framing — `ChatClient.stream()` into typed `AgentEvent` streams | Session storage — Redis, JDBC |
| Question bridge — `CompletableFuture` coordination, timeouts, thread safety | Authentication, error handling |
| Event type contract — `RESPONSE_CHUNK`, `TOOL_CALL_STARTED`, `QUESTION_REQUIRED` | |

The callback-based `Consumer<AgentEvent>` API means any transport can receive events:

```java
// gRPC
session.submitStream(toAgentRequest(req), event -> observer.onNext(toGrpcEvent(event)));

// Kafka
session.submitStream(request, event -> kafkaTemplate.send("agent-events", event));
```

## Relationship to Other Projects

This is a companion project to [spring-ai-agent-utils](https://github.com/spring-ai-community/spring-ai-agent-utils). While agent-utils provides building blocks for creating agents (tools, skills, subagents, task management, interactive feedback), this project adds the serving infrastructure. It also works independently with any Spring AI `ChatClient`, even without agent-utils on the classpath.

This project is not a workflow engine, an orchestration framework, or a new agent abstraction. It handles sessions, streaming, and transport for agents that already work locally.

### Related Frameworks and Platforms

Agent frameworks on the JVM and beyond focus on agent construction — models, tools, memory, orchestration. The serving layer (sessions, streaming, transport) is a separate concern that teams typically build per-application.

**JVM** — [Spring AI](https://docs.spring.io/spring-ai/reference/), [LangChain4j](https://docs.langchain4j.dev/), [Semantic Kernel](https://learn.microsoft.com/en-us/semantic-kernel/overview/), [Embabel](https://github.com/embabel/embabel-agent), [Koog](https://github.com/JetBrains/koog) all focus on agent authoring. None include a reusable hosting/serving layer.

**Protocols** — [MCP](https://modelcontextprotocol.io/) (tool discovery) and [A2A](https://google.github.io/A2A/) (agent-to-agent communication) are complementary. MCP tools work inside agents served by this project.

**Python/TypeScript** — [LangGraph Platform](https://langchain-ai.github.io/langgraph/concepts/langgraph_platform/) is the closest equivalent (per-thread isolation, SSE streaming, human-in-the-loop), but Python-only and tied to LangGraph state machines. [Vercel AI SDK](https://sdk.vercel.ai/docs) handles streaming but not sessions. [Chainlit](https://github.com/Chainlit/chainlit) provides sessions and HITL but is community-maintained.

**Cloud** — [OpenAI Responses API](https://platform.openai.com/docs/api-reference/responses), [AWS Bedrock AgentCore](https://aws.amazon.com/bedrock/agentcore/), and [Google Vertex AI Agent Engine](https://cloud.google.com/vertex-ai/generative-ai/docs/agent-engine/overview) offer managed agent hosting within their respective ecosystems.

This project provides a self-hosted, model-agnostic alternative for the Spring AI ecosystem.

## Project Structure

```
spring-ai-agent-serve/
+-- spring-ai-agent-serve-core/              # Sessions, events, tool observability, question bridge
+-- spring-ai-agent-serve-sse/               # SSE + REST transport
+-- spring-ai-agent-serve-websocket/         # WebSocket (STOMP) transport
+-- spring-ai-agent-serve-starter-sse/       # Spring Boot starter for SSE
+-- spring-ai-agent-serve-starter-websocket/ # Spring Boot starter for WebSocket
+-- examples/
    +-- sse/
    |   +-- chatbot-demo/                   # Browser chat UI over SSE
    |   +-- cli-client/                     # Terminal client over SSE
    +-- websocket/
    |   +-- chatbot-demo/                   # Browser chat UI over WebSocket
    |   +-- cli-client/                     # Terminal client over STOMP
    +-- programmatic-demo/                  # Core library only, no transport
```

Each transport module is self-contained with its own auto-configuration and properties. The core module has zero transport dependencies.

## Current Status

Early release (0.1.0-SNAPSHOT). Two transports available: SSE+REST (recommended) and WebSocket (STOMP).

**Planned:**
- Kafka and AMQP transports
- Pluggable session storage (Redis, JDBC)
- Request cancellation
- Observability (Micrometer metrics, Actuator health)

## Requirements

- Java 17+
- Spring Boot 4.0.0+
- Spring AI 2.0.0-M2+

## License

Apache License 2.0
