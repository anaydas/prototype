import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * Minimal Java SSE server (no external dependencies).
 * - GET /          → serves public/index.html
 * - GET /style.css → serves public/style.css
 * - GET /app.js    → serves public/app.js
 * - GET /logs/stream → SSE endpoint, streams workflow.log lines in loop
 */
public class SseServer {

    private static final int    PORT     = 3000;
    private static final String LOG_FILE = "logs/workflow.log";
    private static final long   LINE_DELAY_MS  = 120;  // delay between lines
    private static final long   LOOP_PAUSE_MS  = 2000; // pause between loops

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        server.createContext("/logs/stream", new SseHandler());
        server.createContext("/",            new StaticHandler());

        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        System.out.println("\n🚀 SSE Server started at http://localhost:" + PORT);
        System.out.println("   Streaming logs from: " + LOG_FILE + "\n");
    }

    // ──────────────────────────────────────────────
    // SSE Handler
    // ──────────────────────────────────────────────
    static class SseHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // CORS
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Content-Type",  "text/event-stream");
            exchange.getResponseHeaders().add("Cache-Control", "no-cache");
            exchange.getResponseHeaders().add("Connection",    "keep-alive");
            exchange.sendResponseHeaders(200, 0); // 0 = chunked / streaming

            OutputStream out = exchange.getResponseBody();
            boolean running = true;
            int loopCount = 0;

            try {
                List<String> lines = Files.readAllLines(Paths.get(LOG_FILE));

                while (running) {
                    loopCount++;
                    // Send "start" event so frontend knows a new loop began
                    writeEvent(out, "start", "{\"loop\":" + loopCount + "}");

                    for (int i = 0; i < lines.size(); i++) {
                        String raw  = lines.get(i);
                        String type = detectType(raw);
                        String json = "{\"line\":" + jsonString(raw)
                                    + ",\"type\":\"" + type + "\""
                                    + ",\"index\":" + i + "}";
                        writeEvent(out, "log", json);
                        Thread.sleep(LINE_DELAY_MS);
                    }

                    // Pause before next loop
                    Thread.sleep(LOOP_PAUSE_MS);
                }
            } catch (InterruptedException | IOException e) {
                // Client disconnected — clean exit
                System.out.println("[SSE] Client disconnected (loop " + loopCount + ").");
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

        /** Minimal JSON string escaping */
        private String jsonString(String s) {
            String escaped = s.replace("\\", "\\\\")
                              .replace("\"", "\\\"")
                              .replace("\n", "\\n")
                              .replace("\r", "\\r")
                              .replace("\t", "\\t");
            return "\"" + escaped + "\"";
        }
    }

    // ──────────────────────────────────────────────
    // Static File Handler — serves files from public/
    // ──────────────────────────────────────────────
    static class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String uri = exchange.getRequestURI().getPath();
            if (uri.equals("/")) uri = "/index.html";

            File file = new File("public" + uri);
            if (!file.exists() || file.isDirectory()) {
                String body = "404 Not Found";
                exchange.sendResponseHeaders(404, body.length());
                exchange.getResponseBody().write(body.getBytes());
                exchange.getResponseBody().close();
                return;
            }

            String mime = getMimeType(file.getName());
            exchange.getResponseHeaders().add("Content-Type", mime);
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
