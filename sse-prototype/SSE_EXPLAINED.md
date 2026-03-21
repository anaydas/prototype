# SSE (Server-Sent Events) — How It Works

## What is SSE?

SSE is a **one-way, persistent HTTP connection** where the **server pushes data to the browser** — the client just listens. Unlike WebSockets (which are bidirectional), SSE is intentionally simple and one-directional.

---

## The SSE Protocol (it's just HTTP!)

When the browser opens an SSE connection, it sends a normal HTTP GET request. The server responds with a special content type and **never closes the connection** — it just keeps writing data:

```
HTTP/1.1 200 OK
Content-Type: text/event-stream   ← magic header
Cache-Control: no-cache
Connection: keep-alive

event: log
data: {"line":"Hello World"}

event: log
data: {"line":"Second line"}

                                  ← server keeps writing forever
```

Each "message" is separated by a **blank line (`\n\n`)**. The format is:

```
event: <event-name>\n
data: <payload>\n
\n
```

The browser's built-in `EventSource` API handles all reconnection, parsing, and buffering automatically.

---

## How the Java Backend Works

### Step 1 — The HTTP Server

```java
HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
server.createContext("/logs/stream", new SseHandler());
server.createContext("/",            new StaticHandler());
```

`HttpServer` is Java's **built-in HTTP server** (part of `com.sun.net.httpserver`).

- `createContext(path, handler)` — registers a **handler** for a URL path. Every request to `/logs/stream` is routed to `SseHandler`.
- `setExecutor(Executors.newCachedThreadPool())` — each request runs on its own thread, so multiple clients don't block each other.

---

### Step 2 — The SSE Handler

```java
static class SseHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
```

`HttpHandler` is an **interface** with one method: `handle(HttpExchange)`. You implement it to define what happens when a request arrives.

`HttpExchange` is the request+response object — contains headers, body stream, URI, etc.

**Setting the SSE response headers:**
```java
exchange.getResponseHeaders().add("Content-Type",  "text/event-stream");
exchange.getResponseHeaders().add("Cache-Control", "no-cache");
exchange.getResponseHeaders().add("Connection",    "keep-alive");
exchange.sendResponseHeaders(200, 0);  // 0 = streaming/chunked (no fixed Content-Length)
```

`sendResponseHeaders(200, 0)` is key — passing `0` as content-length tells Java to use **chunked transfer encoding**, which means the connection stays open and data is flushed incrementally.

**The streaming loop:**
```java
OutputStream out = exchange.getResponseBody();

while (running) {
    for (String line : lines) {
        String msg = "event: log\ndata: " + json + "\n\n";
        out.write(msg.getBytes("UTF-8"));
        out.flush();           // ← CRITICAL: push bytes to browser immediately
        Thread.sleep(120);
    }
    Thread.sleep(2000);        // pause between loops
}
```

`out.flush()` is critical — without it, data sits in a buffer and the browser never sees it.

**Detecting client disconnect:**

When the browser closes the tab, the next `out.write()` throws an `IOException` — that's how your Java code knows the client disconnected and stops the loop.

```java
} catch (IOException e) {
    // Client disconnected — clean exit
}
```

---

## Multiple Workflow Runs — Separate Endpoints

### Approach 1 — Path-based routing (simplest)

Register a **single handler** on a prefix and extract the run ID from the URL:

```java
server.createContext("/logs/", new SseHandler());
// Handles: /logs/1842/stream, /logs/1843/stream, etc.
```

```java
static class SseHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // URL: /logs/1842/stream
        String path  = exchange.getRequestURI().getPath(); // "/logs/1842/stream"
        String runId = path.split("/")[2];                 // "1842"

        File logFile = new File("logs/run-" + runId + ".log");
        if (!logFile.exists()) { /* 404 */ return; }

        // Stream that specific run's log file...
        List<String> lines = Files.readAllLines(logFile.toPath());
        // ... same SSE loop as before
    }
}
```

Each browser tab connects to its own URL (`/logs/1842/stream` vs `/logs/1843/stream`) and gets its own independent thread streaming its own log file.

---

### Approach 2 — Live broadcast with multiple subscribers (pub/sub)

If the log source is **live** (e.g., a real build process writing to a shared buffer), you want a **pub/sub fan-out** model:

```
Build Process → writes lines → RunSession [runId=1842]
                                    ├── subscriber (Browser Tab A)
                                    ├── subscriber (Browser Tab B)
                                    └── subscriber (Browser Tab C)
```

```java
// A registry of active runs
Map<String, RunSession> activeSessions = new ConcurrentHashMap<>();

static class RunSession {
    final String runId;
    final List<OutputStream> subscribers = new CopyOnWriteArrayList<>();

    // Called by build process to push a new log line
    void broadcast(String line) {
        String msg = "event: log\ndata: " + line + "\n\n";
        for (OutputStream out : subscribers) {
            try {
                out.write(msg.getBytes());
                out.flush();
            } catch (IOException e) {
                subscribers.remove(out); // dead client
            }
        }
    }
}

// SSE handler just subscribes *this* browser connection to the session
static class SseHandler implements HttpHandler {
    void handle(HttpExchange exchange) throws IOException {
        String runId      = parseRunId(exchange);
        RunSession session = activeSessions.get(runId);

        exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(200, 0);

        OutputStream out = exchange.getResponseBody();
        session.subscribers.add(out);  // register this tab

        // Block this thread until client disconnects
        try { Thread.currentThread().join(); }
        catch (InterruptedException e) { session.subscribers.remove(out); }
    }
}
```

---

## Summary

| Concept | What it does |
|---|---|
| `text/event-stream` | Tells browser to keep connection open for SSE |
| `sendResponseHeaders(200, 0)` | Starts streaming with no fixed content length |
| `out.flush()` | Immediately sends buffered bytes to client |
| `IOException` on write | Signals the client disconnected |
| `HttpHandler` | Interface you implement to handle a route |
| `createContext(path, handler)` | Binds a URL prefix to a handler |
| `CachedThreadPool` | Each client SSE connection = its own thread |
| Path-based routing | `/logs/{runId}/stream` for isolated per-run streams |
| Pub/sub `RunSession` | Fan-out one data source to many browser tabs |

> **Core insight:** SSE is just a **forever-open HTTP response**. All the "magic" is simply writing `event:\ndata:\n\n` formatted strings to the response output stream and flushing regularly.
