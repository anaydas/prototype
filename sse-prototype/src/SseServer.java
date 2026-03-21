import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * Java SSE server with path-based routing for multiple workflow runs.
 *
 * Routes:
 *   GET /               → public/index.html  (home: list of runs)
 *   GET /viewer.html    → public/viewer.html (log viewer for a specific run)
 *   GET /style.css      → public/style.css
 *   GET /app.js         → public/app.js
 *   GET /logs/{runId}/stream → SSE stream for logs/run-{runId}.log
 */
public class SseServer {

    private static final int  PORT          = 3000;
    private static final long LINE_DELAY_MS = 120;   // ms between log lines
    private static final long LOOP_PAUSE_MS = 2000;  // ms pause before replaying

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // Path-based SSE handler — matches /logs/*/stream
        server.createContext("/logs/", new SseHandler());

        // Static file handler — serves everything else from public/
        server.createContext("/", new StaticHandler());

        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        System.out.println("\n🚀 SSE Server started at http://localhost:" + PORT);
        System.out.println("   SSE endpoints:");
        System.out.println("     http://localhost:" + PORT + "/logs/1842/stream");
        System.out.println("     http://localhost:" + PORT + "/logs/1843/stream\n");
    }

    // ──────────────────────────────────────────────────────────
    // SSE Handler — /logs/{runId}/stream
    // Extracts runId from URL, finds logs/run-{runId}.log, streams it.
    // ──────────────────────────────────────────────────────────
    static class SseHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {

            // ── Parse runId from path: /logs/1842/stream ──
            String path  = exchange.getRequestURI().getPath(); // e.g. "/logs/1842/stream"
            String[] segments = path.split("/");               // ["", "logs", "1842", "stream"]

            if (segments.length < 4 || !segments[3].equals("stream")) {
                send404(exchange, "Invalid SSE path. Use /logs/{runId}/stream");
                return;
            }

            String runId   = segments[2];
            File   logFile = new File("logs/run-" + runId + ".log");

            if (!logFile.exists()) {
                send404(exchange, "No log file found for run: " + runId);
                return;
            }

            // ── SSE response headers ──
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Content-Type",  "text/event-stream");
            exchange.getResponseHeaders().add("Cache-Control", "no-cache");
            exchange.getResponseHeaders().add("Connection",    "keep-alive");
            exchange.sendResponseHeaders(200, 0); // 0 = chunked/streaming

            System.out.println("[SSE] Client connected → run #" + runId);

            OutputStream out      = exchange.getResponseBody();
            int          loopCount = 0;

            try {
                List<String> lines = Files.readAllLines(logFile.toPath());

                while (true) {
                    loopCount++;
                    // Notify frontend that a new loop/replay is starting
                    writeEvent(out, "start", "{\"loop\":" + loopCount + ",\"runId\":\"" + runId + "\"}");

                    for (int i = 0; i < lines.size(); i++) {
                        String raw  = lines.get(i);
                        String type = detectType(raw);
                        String json = "{\"line\":" + jsonString(raw)
                                    + ",\"type\":\"" + type + "\""
                                    + ",\"index\":" + i + "}";
                        writeEvent(out, "log", json);
                        Thread.sleep(LINE_DELAY_MS);
                    }

                    Thread.sleep(LOOP_PAUSE_MS);
                }
            } catch (InterruptedException | IOException e) {
                System.out.println("[SSE] Client disconnected ← run #" + runId + " (loop " + loopCount + ")");
            } finally {
                try { out.close(); } catch (IOException ignored) {}
            }
        }

        private void writeEvent(OutputStream out, String event, String data) throws IOException {
            String msg = "event: " + event + "\ndata: " + data + "\n\n";
            out.write(msg.getBytes("UTF-8"));
            out.flush();
        }

        private String detectType(String line) {
            if (line.contains("[SUCCESS]")) return "success";
            if (line.contains("[ERROR]"))   return "error";
            if (line.contains("[WARNING]")) return "warning";
            if (line.contains("[STEP]"))    return "step";
            return "info";
        }

        private String jsonString(String s) {
            return "\"" + s.replace("\\", "\\\\")
                           .replace("\"", "\\\"")
                           .replace("\n", "\\n")
                           .replace("\r", "\\r")
                           .replace("\t", "\\t") + "\"";
        }

        private void send404(HttpExchange ex, String msg) throws IOException {
            byte[] body = msg.getBytes("UTF-8");
            ex.sendResponseHeaders(404, body.length);
            ex.getResponseBody().write(body);
            ex.getResponseBody().close();
        }
    }

    // ──────────────────────────────────────────────────────────
    // Static File Handler — serves files from public/
    // ──────────────────────────────────────────────────────────
    static class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String uri = exchange.getRequestURI().getPath();
            if (uri.equals("/")) uri = "/index.html";

            File file = new File("public" + uri);
            if (!file.exists() || file.isDirectory()) {
                byte[] body = "404 Not Found".getBytes();
                exchange.sendResponseHeaders(404, body.length);
                exchange.getResponseBody().write(body);
                exchange.getResponseBody().close();
                return;
            }

            exchange.getResponseHeaders().add("Content-Type", getMimeType(file.getName()));
            exchange.sendResponseHeaders(200, file.length());
            try (InputStream in = new FileInputStream(file);
                 OutputStream out = exchange.getResponseBody()) {
                in.transferTo(out);
            }
        }

        private String getMimeType(String name) {
            if (name.endsWith(".html")) return "text/html; charset=UTF-8";
            if (name.endsWith(".css"))  return "text/css; charset=UTF-8";
            if (name.endsWith(".js"))   return "application/javascript";
            return "application/octet-stream";
        }
    }
}
