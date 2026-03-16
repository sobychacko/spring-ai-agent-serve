# WebSocket CLI Client

Terminal client that connects to a running WebSocket chatbot server and interacts with the agent from the command line.

## Prerequisites

- Java 17+
- A running WebSocket chatbot server (see [WebSocket Chatbot Demo](../chatbot-demo))

## Run

**Step 1:** Start the WebSocket chatbot demo server in one terminal:

```bash
export ANTHROPIC_API_KEY=your-key-here
mvn spring-boot:run -pl examples/websocket/chatbot-demo
```

**Step 2:** In a separate terminal, start the CLI client:

```bash
mvn spring-boot:run -pl examples/websocket/cli-client
```

The client connects to `ws://localhost:8080/ws`, opens a STOMP session, and gives you a `You>` prompt.

To connect to a server on a different host or port:

```bash
mvn spring-boot:run -pl examples/websocket/cli-client -Dspring-boot.run.arguments="--serve.server.url=http://myserver:9090"
```

## What It Shows

- Streaming responses appear token-by-token in the terminal
- Tool call indicators show when the agent invokes tools
- When the agent asks a question, numbered options are displayed — type the number or a free-text answer
- Demonstrates that the WebSocket/STOMP transport works with any STOMP client, not just browsers

## How It Works

The client uses Spring's `WebSocketStompClient` with SockJS to connect to the server. It subscribes to `/topic/agent/{sessionId}` for events and sends messages to `/app/agent`. A `StompSessionHandler` processes incoming events on a background thread while the main thread reads stdin.
