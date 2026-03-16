# SSE Chatbot Demo

Browser-based chat UI that connects to a Spring AI agent over Server-Sent Events.

## Prerequisites

- Java 17+
- `ANTHROPIC_API_KEY` environment variable set

## Run

From the project root:

```bash
export ANTHROPIC_API_KEY=your-key-here
mvn spring-boot:run -pl examples/sse/chatbot-demo
```

Open http://localhost:8080 in a browser.

## Try with curl

You can also interact with the server from the command line without the browser or CLI client.

Open an SSE event stream (this blocks and prints events as they arrive):

```bash
curl -N http://localhost:8080/api/agent/sessions/new/events
```

The first event gives you a session ID:

```
event:SESSION_CREATED
data:{"sessionId":"a1b2c3d4-...","type":"SESSION_CREATED","content":null,"metadata":{"sessionId":"a1b2c3d4-..."}}
```

In a separate terminal, send a message using that session ID:

```bash
curl -X POST http://localhost:8080/api/agent/sessions/a1b2c3d4-.../messages \
  -H "Content-Type: application/json" \
  -d '{"sessionId": "a1b2c3d4-...", "message": "What files are in this project?"}'
```

Streaming events appear in the first terminal as the agent works.

## What It Shows

- Streaming responses appear token-by-token as the agent generates them
- Tool call indicators show when the agent invokes tools (GrepTool, GlobTool, etc.)
- If the agent needs clarification, a question card with options appears in the chat
- Each browser tab gets an isolated session with its own conversation memory

## How It Works

The app defines two beans — a `ChatClient.Builder` with a system prompt and a `List<ToolCallback>` with agent-utils tools. Everything else is auto-configured by the SSE starter: session management, streaming, event framing, and the question bridge.

The browser uses the native `EventSource` API to receive events and `fetch()` to send messages. No WebSocket, no STOMP, no SockJS.
