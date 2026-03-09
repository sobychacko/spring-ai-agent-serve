# Spring AI Agent Serve

A framework that helps your Spring Boot application serve AI agents — handling sessions, streaming, event framing, and transport so you don't have to.

## Why "Serve"?

A Spring AI agent is a `ChatClient` with tools. It works perfectly in a single-process application. The challenge comes when you need to **serve** that agent to remote clients — browsers, CLIs, mobile apps, or other services.

That's what this framework does. It doesn't host your agent (you do — it's your Spring Boot app). It doesn't define what your agent does (that's `ChatClient`, tools, and advisors). It gives your application the infrastructure to **serve** the agent: sessions, streaming, tool call events, interactive question bridging, and transport.

The name captures the relationship precisely: your application embeds `spring-ai-agent-serve` the way it embeds `spring-boot-starter-web`. The starter doesn't *host* your controllers — it lets your app *serve* HTTP. Similarly, this framework doesn't host your agent — it lets your app serve the agent over WebSocket, SSE, Kafka, or whatever transport your infrastructure requires.

Think of this the way you think of Tomcat for servlets. Tomcat doesn't define what a servlet does — it manages the lifecycle, handles connections, isolates requests, and provides the runtime environment. The serve layer does the same for `ChatClient`-based agents: you define agent behavior with `ChatClient.Builder`, tools, and advisors; the serve layer manages sessions, streams responses, emits tool call events, bridges interactive questions to remote clients, and exposes the agent over a network transport.

This is a companion project to [spring-ai-agent-utils](https://github.com/spring-ai-community/spring-ai-agent-utils). While agent-utils provides the building blocks for creating agents (tools, skills, subagents, task management, interactive feedback), the serve layer adds the serving infrastructure on top. It also works independently with any Spring AI `ChatClient`, even without agent-utils on the classpath.

The serve layer is not a workflow engine, an orchestration framework, or a new agent abstraction. It is infrastructure for serving agents that already work locally.

## Why Not Just Use ChatClient Directly?

For plain chat, you absolutely should. A REST controller returning `Flux<String>` over SSE works well — send a message, stream back text. Spring AI's `ChatClient` handles that out of the box.

The gap appears when your `ChatClient` becomes an agent:

- **Tool calls take time.** Without event framing, the UI shows a frozen spinner while the agent runs tools. The serve layer emits `TOOL_CALL_STARTED` and `TOOL_CALL_COMPLETED` events so clients can show what's happening in real time.
- **Agents ask questions back.** When the agent calls `AskUserQuestionTool` mid-stream, the user needs to answer before the agent can continue. The serve layer pushes the question to the client and waits for the answer before resuming the agent.
- **Multiple clients need isolation.** Two users hitting the same endpoint shouldn't see each other's conversation history. The serve layer creates per-session memory and `ChatClient` instances automatically.
- **Concurrent requests corrupt state.** Two messages to the same session at once can interleave tool executions and corrupt memory. The serve layer serializes requests per session.

If none of this applies to you, use `ChatClient` directly. If it does, that's what this framework is for.

## When Do You Need This?

**Building a chat UI that talks to a Spring AI agent?** You need session isolation, streaming, and a network endpoint. The serve layer gives you that out of the box — add the starter, define a `ChatClient.Builder` bean, and your agent is accessible from a browser.
See the [Chatbot Demo](examples/serve-chatbot-demo).

**Want a CLI or desktop app that connects to a shared agent server?** Multiple clients can connect to the same server, each with isolated sessions and memory.
See the [CLI Client](examples/serve-cli-client).

**Just need session management and streaming in a standalone app — no transport at all?** The core library works without WebSocket. Use `AgentSessionManager` and `session.submitStream()` directly with a `Consumer<AgentEvent>` callback. You get per-session memory, tool call events, and the question bridge — all in-process.
See the [Programmatic Demo](examples/serve-programmatic-demo).

## Landscape

Agent frameworks — on the JVM and beyond — focus on agent construction: models, tools, memory, orchestration. The serving layer (sessions, streaming, transport) is a separate concern that teams typically build per-application. This section covers agent frameworks, protocols, serving platforms, and where this project fits.

### JVM Frameworks

These focus on agent construction. This project adds a serving layer specifically for Spring AI's `ChatClient`.

- [**Spring AI**](https://docs.spring.io/spring-ai/reference/) — `ChatClient`, model abstractions, advisors, tool calling, memory, and [MCP support](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-overview.html). This project builds directly on Spring AI's `ChatClient` API.
- [**LangChain4j**](https://docs.langchain4j.dev/) — `AiServices`, `ChatMemoryProvider`, RAG pipelines. The [Quarkus integration](https://docs.quarkiverse.io/quarkus-langchain4j/dev/index.html) adds `@MemoryId` for per-user memory scoping. v1.3.0 added [`langchain4j-agentic`](https://docs.langchain4j.dev/tutorials/agentic) for multi-step orchestration.
- [**Semantic Kernel (Java)**](https://learn.microsoft.com/en-us/semantic-kernel/overview/) — Microsoft's AI orchestration SDK (Java, C#, Python). Plugin system, chat history, prompt templates. Integrates with [Azure AI Foundry](https://learn.microsoft.com/en-us/azure/ai-foundry/) and the [Microsoft Agent Framework](https://devblogs.microsoft.com/semantic-kernel/microsofts-agentic-ai-frameworks-autogen-and-semantic-kernel/).
- [**Embabel**](https://github.com/embabel/embabel-agent) — Kotlin/JVM agent framework by Rod Johnson. Builds on Spring AI, adds Goal-Oriented Action Planning (GOAP) for deterministic multi-step workflows.
- [**Koog**](https://github.com/JetBrains/koog) — Kotlin Multiplatform agent framework by JetBrains. Graph-based execution, [A2A](https://google.github.io/A2A/) support, agent persistence with PostgreSQL. Targets JVM, Android, iOS, JS, and Wasm.

### Protocols

- [**MCP**](https://modelcontextprotocol.io/) — Standardizes how LLM applications discover and invoke tools. Complementary — MCP tools can be used inside agents served by this framework. Spring AI supports MCP on both [client and server](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-overview.html) sides.
- [**A2A**](https://google.github.io/A2A/) — Google's protocol for agent-to-agent communication (discovery via Agent Cards, task delegation, streaming results). Complementary — an agent served by this framework could use A2A to delegate to other agents.

### AI Gateways

[**Portkey**](https://portkey.ai/), [**LiteLLM**](https://github.com/BerriAI/litellm), [**Kong AI Gateway**](https://konghq.com/products/kong-ai-gateway), and [**Bifrost**](https://github.com/maximhq/bifrost) are model API proxies — unified interfaces, routing, cost tracking, and governance for LLM provider APIs. Different architectural layer: they proxy model calls; this project manages agent conversations. Teams often use both together.

### Python / TypeScript

- [**LangGraph Platform**](https://langchain-ai.github.io/langgraph/concepts/langgraph_platform/) — Deploys LangGraph agents as stateful services with per-thread isolation, SSE streaming, checkpointing, and human-in-the-loop via interrupt/resume. Supports time-travel debugging and replay. Python-only, designed for LangGraph state machine agents. Framework is open source; platform infrastructure is part of LangSmith's commercial offering.
- [**Chainlit**](https://github.com/Chainlit/chainlit) — Full-stack Python framework with sessions, streaming, `AskUserMessage()` for human-in-the-loop, and a polished chat UI. Includes file uploads, multi-modal support, and conversation threading. Now community-maintained.
- [**Vercel AI SDK**](https://sdk.vercel.ai/docs) — TypeScript SDK with `streamText`, `generateText`, and React hooks (`useChat`, `useStream`) for streaming UIs. AI SDK 5/6 added tool call events and a tool approval system. Focuses on the streaming contract; session management is left to the application.
- [**Hayhooks**](https://github.com/deepset-ai/hayhooks) — Serves Haystack pipelines as REST APIs with SSE streaming and OpenAI-compatible endpoints. Python-only.

### Cloud Platforms

- [**OpenAI Responses API**](https://platform.openai.com/docs/api-reference/responses) — Built-in tools, SSE streaming, `requires_action` for human-in-the-loop. The upcoming [Conversations API](https://platform.openai.com/docs/guides/conversation-state) adds persistent multi-turn state. Successor to the Assistants API (deprecated August 2025).
- [**AWS Bedrock AgentCore**](https://aws.amazon.com/bedrock/agentcore/) — Managed agent runtime with Firecracker microVM session isolation, bidirectional WebSocket streaming, and `ReturnControl` for human-in-the-loop. Supports Bedrock-available models.
- [**Google Vertex AI Agent Engine**](https://cloud.google.com/vertex-ai/generative-ai/docs/agent-engine/overview) — Managed agent deployment with session management, topic-based memory, and streaming via the Live API. Integrates with Google's open source [Agent Development Kit (ADK)](https://google.github.io/adk-docs/).

All three are fully managed within their respective cloud ecosystems.

### Where This Project Fits

This project provides a similar serving layer for the Spring AI ecosystem: open source, self-hosted, and model-agnostic. It is an embeddable framework — not a managed platform — that Spring Boot applications include as a dependency.

## Architecture

The serve layer sits between external clients and the Spring AI agent stack, translating between transport protocols and the `ChatClient` Java API:

```
Clients                          Serve                           Agent Stack
---------                        -----                           -----------

Browser ──┐                  ┌─ Transport ──┐                  ┌─ ChatClient
           │                  │  (WebSocket,  │                  │   .prompt()
CLI ───────┼── connect ──────►│   SSE+REST,   ├── delegate ─────►│   .stream()
           │                  │   or direct)  │                  │
App ──────┘                  └──────┬───────┘                  ├─ agent-utils
                                    │                          │   tools, skills
                             AgentSession                      │   subagents
                             (per-client state,                │   advisors
                              memory binding,                  │
                              request queuing)                 ├─ Memory
                                                               │   (per-session)
                                                               │
                                                               └─ AI Model
                                                                   (Claude, OpenAI, etc.)
```

### Layer Responsibilities

| Layer | Responsibility | Does NOT do |
|-------|---------------|-------------|
| **Transport** | Accept connections, frame messages, push events | Agent logic, tool execution |
| **AgentSession** | Session lifecycle, memory binding, request queuing | Transport details |
| **QuestionHandler bridge** | Route AskUserQuestionTool to/from remote clients | Question logic (that's the tool's job) |
| **ChatClient + agent-utils** | All agent behavior — tools, skills, orchestration | Transport, sessions |

All agent behavior remains inside `ChatClient` and agent-utils. The serve layer handles transport, sessions, and protocol bridging.

### Deployment Models

The serve layer supports two deployment models depending on how the enterprise's infrastructure is structured.

#### Model 1: Serve at the Edge

The serve layer is the entry point. Clients connect directly to it.

```
Browser/CLI/App → WebSocket or SSE → Serve → ChatClient → AI Model
```

This is the simple case — one service handles transport, sessions, and agent execution. The demo app uses this model. Suitable for teams building a standalone agent service without existing messaging infrastructure.

#### Model 2: Serve Behind a Message Broker

The serve layer is a backend service in an event-driven pipeline. Clients never talk to it directly.

```
End User → [any protocol] → Web App → Kafka/RabbitMQ → Serve → Kafka/RabbitMQ → Web App → End User
```

The enterprise's web app produces an `AgentRequest` (session ID + message) to a topic/queue. The serve layer consumes it, routes to the right session, runs the agent, and produces `AgentEvent` messages back. The web app consumes those events and delivers them to the user however it wants.

**Why enterprises use this model:** They already have Kafka or RabbitMQ. Their services all communicate through it. Their ops team monitors it, their deployment pipelines are built around it. They're not going to expose a new WebSocket endpoint directly to the internet for an AI agent. They want the agent to be another consumer/producer in the infrastructure they already run.

### Use Cases

#### Edge Deployment (Model 1)

**Internal tools and dashboards** — A team builds an AI-powered DevOps assistant. Developers open a web UI, ask *"Why did the staging deploy fail?"*, and the agent calls a `ci_logs` tool, a `k8s_status` tool, and explains the root cause. The serve layer gives the team session isolation, streaming, and tool call visibility without writing any WebSocket or session management code.

**Customer-facing chat** — A SaaS product adds an AI support agent to its web app. Customers ask questions, the agent calls product-specific tools (account lookup, billing history, feature guides), and streams answers back. Each customer gets an isolated session with conversation memory. The serve layer is the entry point — one Spring Boot app handles everything.

**Interactive agent workflows** — An agent that helps users configure complex systems (cloud infrastructure, data pipelines, insurance quotes). The agent asks clarifying questions mid-conversation using `AskUserQuestionTool`, the serve layer pushes them to the browser, collects answers, and resumes the agent. Without the question bridge, this requires custom bidirectional plumbing per application.

#### Broker-Based Deployment (Model 2)

**Event-driven microservices** — A retailer's customer support platform. Customer asks *"Where's my order?"* in the retailer's app. The app produces the message to Kafka. The serve layer consumes it, calls an order tracking tool (which hits the retailer's fulfillment API), calls a shipping tool to get the updated ETA, and produces response events back. The retailer's app streams them to the customer: *"Your order #4821 shipped Wednesday but is delayed due to weather in Memphis. New estimated delivery is Friday."* The serve layer handles session isolation (customer A's conversation is not customer B's), serial execution, and tool call events so the app can show *"Looking up your order..."* while the agent works.

**Audit/compliance fan-out** — A wealth management firm's AI agent helps advisors draft investment recommendations. Regulations require that every AI-generated recommendation be logged. The serve layer sends events to two places simultaneously: WebSocket to the advisor's browser (real-time streaming) and Kafka to a compliance topic (immutable audit trail of every tool call, response chunk, and final response with timestamps). This is a `CompositeEventSender` pattern — Kafka *in addition to* WebSocket, through the same `AgentEventSender` interface.

**Async batch processing** — An insurance company's claims department. An AI agent processes 200 claim documents overnight — reads each document, calls a `policy_lookup` tool to check coverage, calls a `fraud_signals` tool to flag inconsistencies, and writes a preliminary assessment. Adjusters open their dashboard the next morning and see 200 assessments ready for review. No client is watching the stream, but session isolation still matters (each claim is a separate conversation) and tool call observation matters for operational monitoring.

## What It Does

- **Exposes a ChatClient to remote clients** — currently over WebSocket (STOMP), with SSE+REST planned
- **Manages sessions** — each client gets an isolated session with its own `ChatClient` instance and conversation memory
- **Isolates memory per session** — conversations don't bleed across clients; each session has its own `MessageWindowChatMemory`
- **Serializes requests per session** — concurrent messages to the same session queue up rather than racing, preventing memory corruption and interleaved tool executions
- **Streams responses in real-time** — text chunks, tool call events, and question prompts are pushed to clients as they happen
- **Bridges AskUserQuestionTool** — when the agent needs user input, questions are pushed to the client; answers flow back and the agent resumes (requires agent-utils on classpath)
- **Auto-configures** — add the starter dependency and provide a `ChatClient.Builder` bean; the serve layer activates automatically

## Quick Start

### 1. Add the starter dependency

```xml
<dependency>
    <groupId>org.springaicommunity</groupId>
    <artifactId>spring-ai-agent-serve-starter</artifactId>
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

Tools are declared as a separate `List<ToolCallback>` bean using `ToolCallbacks.from()` to convert `@Tool`-annotated POJOs to `ToolCallback` instances. The auto-configuration wraps them with `ObservableToolCallback` decorators so that tool call lifecycle events (`TOOL_CALL_STARTED`/`TOOL_CALL_COMPLETED`) are emitted to clients in real time. The serve layer detects the `ChatClient.Builder`, creates a session manager, sets up a STOMP WebSocket endpoint at `/ws`, and starts serving.

### 3. Connect a client

Clients send JSON messages to `/app/agent` and subscribe to `/topic/agent/{sessionId}` for responses.

**Request:**
```json
{
  "sessionId": "abc-123",
  "message": "What is Spring AI?"
}
```

**Response** (delivered to `/topic/agent/abc-123` — streaming event sequence):
```json
{"sessionId": "abc-123", "type": "RESPONSE_CHUNK", "content": "Spring AI is", "metadata": {}}
{"sessionId": "abc-123", "type": "RESPONSE_CHUNK", "content": " a framework for...", "metadata": {}}
{"sessionId": "abc-123", "type": "FINAL_RESPONSE", "content": "Spring AI is a framework for...", "metadata": {}}
```

If `sessionId` is null or blank, the server generates a UUID and includes it in the response. The client uses it for subsequent messages to continue the conversation.

## How Sessions Work

```
Client connects with sessionId="abc-123"
  │
  ▼
AgentSessionManager.getOrCreate("abc-123")
  │
  ├── First time? Create new AgentSession:
  │     - Clone the ChatClient.Builder (for isolation)
  │     - Create a new MessageWindowChatMemory for this session
  │     - Bind MessageChatMemoryAdvisor with conversationId="abc-123"
  │     - Build the ChatClient
  │     - Store in session map
  │
  └── Returning? Retrieve existing AgentSession
        - Same ChatClient, same memory
        - Conversation continues
```

Each session gets its own single-threaded executor. Concurrent requests to the same session queue up rather than racing. This prevents memory corruption, interleaved tool executions, and unpredictable conversation state.

## Configuration

```yaml
spring:
  ai:
    agent:
      serve:
        enabled: true                    # default
        max-messages: 500                # messages kept in session memory
        websocket:
          enabled: true                  # default
          endpoint: /ws                  # STOMP endpoint path
          allowed-origins:
            - "http://localhost:*"       # default; restrict in production
        question:
          timeout-minutes: 5              # how long to wait for user answers
```

## Replacing Components

Every auto-configured bean uses `@ConditionalOnMissingBean`. Define your own bean and the default backs off:

```java
// Replace the session manager (e.g., with a Redis-backed implementation)
@Bean
AgentSessionManager agentSessionManager(ChatClient.Builder builder) {
    return new MyRedisSessionManager(builder);
}

// Replace the event sender (e.g., for a different transport)
@Bean
AgentEventSender agentEventSender() {
    return new MyCustomEventSender();
}
```

## Project Structure

```
spring-ai-agent-serve/
├── spring-ai-agent-serve-core/         # Transport-agnostic core (sessions, events, tool observability, question bridge)
├── spring-ai-agent-serve-websocket/    # WebSocket (STOMP) transport
├── spring-ai-agent-serve-starter/      # Spring Boot starter (POM only)
└── examples/
    ├── serve-chatbot-demo/             # Browser-based chat UI over WebSocket
    ├── serve-cli-client/               # Terminal client connecting to a serve server
    └── serve-programmatic-demo/        # Standalone app using the core library directly
```

The core module depends on `spring-ai-client-chat` (provided scope) with zero transport dependencies. The WebSocket module depends on core plus `spring-websocket` and `spring-messaging`. The starter pulls in core, WebSocket, and `spring-boot-starter-websocket`.

## Current Status

This is an early release (0.1.0-SNAPSHOT) providing session management, per-session memory isolation, tool call event framing, and the AskUserQuestionTool bridge for interactive agent-to-user communication. The current transport is WebSocket (STOMP).

### Planned

- **SSE+REST transport** — the same pattern used by claude.ai, OpenAI, and most AI API providers
- **Kafka and AMQP transports** — for the broker-based deployment model
- **Session eviction** — TTL-based cleanup of idle sessions
- **Pluggable session storage** — Redis, JDBC
- **Request cancellation** — interrupt in-flight agent calls
- **Observability** — Micrometer metrics, Actuator health indicator

## Requirements

- Java 17+
- Spring Boot 4.0.0+
- Spring AI 2.0.0-M2+

## License

Apache License 2.0
