# WebSocket Chatbot Demo

Browser-based chat UI that connects to a Spring AI agent over WebSocket using STOMP.

## Prerequisites

- Java 17+
- `ANTHROPIC_API_KEY` environment variable set

## Run

From the project root:

```bash
export ANTHROPIC_API_KEY=your-key-here
mvn spring-boot:run -pl examples/websocket/chatbot-demo
```

Open http://localhost:8080 in a browser.

## What It Shows

- Streaming responses appear token-by-token as the agent generates them
- Tool call indicators show when the agent invokes tools (GrepTool, GlobTool, etc.)
- If the agent needs clarification, a question card with options appears in the chat
- Each browser tab gets an isolated session with its own conversation memory

## SSE vs WebSocket

This demo uses WebSocket with STOMP over SockJS. The [SSE Chatbot Demo](../../sse/chatbot-demo) provides the same functionality using Server-Sent Events, which is the recommended transport — it works through proxies and load balancers more easily and uses the browser's native `EventSource` API.

## How It Works

The app defines two beans — a `ChatClient.Builder` with a system prompt and a `List<ToolCallback>` with agent-utils tools. The WebSocket starter auto-configures STOMP messaging, session management, streaming, and the question bridge.

The browser connects via SockJS, subscribes to `/topic/agent/{sessionId}` for events, and sends messages to `/app/agent`.
