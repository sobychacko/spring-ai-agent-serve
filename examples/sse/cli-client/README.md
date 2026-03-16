# SSE CLI Client

Terminal client that connects to a running SSE chatbot server and interacts with the agent from the command line.

## Prerequisites

- Java 17+
- A running SSE chatbot server (see [SSE Chatbot Demo](../chatbot-demo))

## Run

**Step 1:** Start the SSE chatbot demo server in one terminal:

```bash
export ANTHROPIC_API_KEY=your-key-here
mvn spring-boot:run -pl examples/sse/chatbot-demo
```

**Step 2:** In a separate terminal, start the CLI client:

```bash
mvn spring-boot:run -pl examples/sse/cli-client
```

The client connects to `http://localhost:8080`, opens an SSE event stream, and gives you a `You>` prompt.

To connect to a server on a different host or port:

```bash
mvn spring-boot:run -pl examples/sse/cli-client -Dspring-boot.run.arguments="--serve.server.url=http://myserver:9090"
```

## What It Shows

- Streaming responses appear token-by-token in the terminal
- Tool call indicators show when the agent invokes tools
- When the agent asks a question, numbered options are displayed — type the number or a free-text answer
- Demonstrates that the SSE transport works with any HTTP client, not just browsers

## How It Works

The client opens a raw HTTP connection to the SSE event stream endpoint (`GET /api/agent/sessions/new/events`) and parses SSE frames manually. Messages are sent via `RestClient` to the REST POST endpoints. A background thread reads the SSE stream while the main thread reads stdin.
