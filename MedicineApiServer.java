import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * Small HTTP server exposing a /api/medicines REST API.
 * - GET /api/medicines -> returns JSON list
 * - POST /api/medicines -> create (expects JSON body with fields)
 * - DELETE /api/medicines?id=<id> -> delete by id
 *
 * Server proxies to Supabase if SUPABASE_URL and SUPABASE_SERVICE_KEY env vars are set.
 */
public class MedicineApiServer {
    private static final int PORT = 8001;
    private final SupabaseClient supabase;
    private final List<String> inMemory = new ArrayList<>(); // simple JSON strings for demo

    public MedicineApiServer(SupabaseClient supabase) {
        this.supabase = supabase;
    }

    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/api/medicines", new MedicinesHandler());
        // Serve static files from the current working directory at the root path
        server.createContext("/", new StaticFileHandler());
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
        String apiUrl = "http://localhost:" + PORT + "/api/medicines";
        String frontendUrl = "http://localhost:" + PORT;
        System.out.println("\nMedicine Inventory System Started!");
        System.out.println("---------------------------");
        System.out.println("Frontend URL: " + frontendUrl);
        System.out.println("API URL: " + apiUrl);
        System.out.println("---------------------------");
        
        // Try to open the browser automatically
        try {
            java.awt.Desktop.getDesktop().browse(java.net.URI.create(frontendUrl));
            System.out.println("Browser opened automatically. If not, visit: " + frontendUrl);
        } catch (Exception e) {
            System.out.println("Please open this URL in your browser: " + frontendUrl);
        }
    }

    /**
     * Simple static file handler that serves files from the working directory.
     * Requests to '/' will return index.html.
     */
    static class StaticFileHandler implements com.sun.net.httpserver.HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String uriPath = exchange.getRequestURI().getPath();
            if (uriPath == null || uriPath.equals("/")) uriPath = "/index.html";
            // sanitize path to prevent directory traversal
            uriPath = uriPath.replace('/', java.io.File.separatorChar);
            if (uriPath.contains("..")) {
                exchange.sendResponseHeaders(403, -1);
                return;
            }
            java.nio.file.Path cwd = java.nio.file.Paths.get("").toAbsolutePath();
            java.nio.file.Path filePath = cwd.resolve(uriPath.substring(1)).normalize();
            if (!filePath.toFile().exists() || filePath.toFile().isDirectory()) {
                // try serve index.html for SPA routes
                filePath = cwd.resolve("index.html");
            }
            byte[] data;
            try {
                data = java.nio.file.Files.readAllBytes(filePath);
            } catch (IOException e) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            String contentType = guessContentType(filePath.toString());
            exchange.getResponseHeaders().add("Content-Type", contentType);
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, data.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(data);
            }
        }

        private String guessContentType(String path) {
            path = path.toLowerCase();
            if (path.endsWith(".html") || path.endsWith(".htm")) return "text/html; charset=utf-8";
            if (path.endsWith(".js")) return "application/javascript; charset=utf-8";
            if (path.endsWith(".css")) return "text/css; charset=utf-8";
            if (path.endsWith(".json")) return "application/json; charset=utf-8";
            if (path.endsWith(".png")) return "image/png";
            if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return "image/jpeg";
            if (path.endsWith(".svg")) return "image/svg+xml";
            if (path.endsWith(".ico")) return "image/x-icon";
            return "application/octet-stream";
        }
    }

    class MedicinesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                addCors(exchange.getResponseHeaders());
                String method = exchange.getRequestMethod();
                if ("OPTIONS".equalsIgnoreCase(method)) {
                    exchange.sendResponseHeaders(204, -1);
                    return;
                }
                if ("GET".equalsIgnoreCase(method)) {
                    handleGet(exchange);
                } else if ("POST".equalsIgnoreCase(method)) {
                    handlePost(exchange);
                } else if ("PUT".equalsIgnoreCase(method)) {
                    handlePut(exchange);
                } else if ("DELETE".equalsIgnoreCase(method)) {
                    handleDelete(exchange);
                } else {
                    sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
                }
            } catch (Exception e) {
                e.printStackTrace();
                sendJson(exchange, 500, "{\"error\":\"Internal server error\"}");
            }
        }

        private void handleGet(HttpExchange exchange) throws IOException {
            if (supabase != null) {
                try {
                    String json = supabase.getAllMedicinesRaw();
                    sendJson(exchange, 200, json == null ? "[]" : json);
                } catch (Exception e) {
                    e.printStackTrace();
                    sendJson(exchange, 502, "{\"error\":\"Failed to fetch from Supabase\"}");
                }
            } else {
                // return in-memory list
                String body = "[" + String.join(",", inMemory) + "]";
                sendJson(exchange, 200, body);
            }
        }

        private void handlePost(HttpExchange exchange) throws IOException {
            String body = readStream(exchange.getRequestBody());
            // naive JSON parsing for expected fields
            String name = getJsonString(body, "name");
            String batch = getJsonString(body, "batch");
            int quantity = getJsonInt(body, "quantity", 0);
            double price = getJsonDouble(body, "price", 0.0);
            String expiry = getJsonString(body, "expiry");
            int minStock = getJsonInt(body, "min_stock", 0);

            if (supabase != null) {
                try {
                    boolean ok = supabase.addMedicine(name, quantity, expiry, price, minStock);
                    if (ok) sendJson(exchange, 201, "{\"success\":true}");
                    else sendJson(exchange, 502, "{\"error\":\"Supabase insert failed\"}");
                } catch (Exception e) {
                    e.printStackTrace();
                    sendJson(exchange, 502, "{\"error\":\"Supabase insert exception\"}");
                }
            } else {
                // store simple JSON object in-memory
                String obj = String.format("{\"id\":\"local-%d\",\"name\":\"%s\",\"batch\":\"%s\",\"quantity\":%d,\"price\":%s,\"expiry\":\"%s\",\"min_stock\":%d}",
                        System.currentTimeMillis(), esc(name), esc(batch), quantity, Double.toString(price), esc(expiry), minStock);
                inMemory.add(obj);
                sendJson(exchange, 201, obj);
            }
        }

        private void handleDelete(HttpExchange exchange) throws IOException {
            // expect query ?id=<id>
            String query = exchange.getRequestURI().getQuery();
            String id = null;
            if (query != null) {
                for (String part : query.split("&")) {
                    String[] kv = part.split("=", 2);
                    if (kv.length == 2 && "id".equals(kv[0])) {
                        id = kv[1];
                        break;
                    }
                }
            }
            if (id == null) {
                sendJson(exchange, 400, "{\"error\":\"Missing id\"}");
                return;
            }
            if (supabase != null) {
                try {
                    boolean ok = supabase.deleteById(id);
                    if (ok) sendJson(exchange, 200, "{\"success\":true}");
                    else sendJson(exchange, 502, "{\"error\":\"Supabase delete failed\"}");
                } catch (Exception e) {
                    e.printStackTrace();
                    sendJson(exchange, 502, "{\"error\":\"Supabase delete exception\"}");
                }
            } else {
                // remove from in-memory by id
                final String idFinal = id;
                boolean removed = inMemory.removeIf(s -> s.contains("\"id\":\"" + idFinal + "\""));
                if (removed) sendJson(exchange, 200, "{\"success\":true}");
                else sendJson(exchange, 404, "{\"error\":\"Not found\"}");
            }
        }

        private void handlePut(HttpExchange exchange) throws IOException {
            String body = readStream(exchange.getRequestBody());
            String id = getJsonString(body, "id");
            int quantity = getJsonInt(body, "quantity", -1);
            if (id == null || id.isEmpty() || quantity < 0) {
                sendJson(exchange, 400, "{\"error\":\"Missing id or quantity\"}");
                return;
            }
            if (supabase != null) {
                try {
                    boolean ok = supabase.updateQuantityById(id, quantity);
                    if (ok) sendJson(exchange, 200, "{\"success\":true}");
                    else sendJson(exchange, 502, "{\"error\":\"Supabase update failed\"}");
                } catch (Exception e) {
                    e.printStackTrace();
                    sendJson(exchange, 502, "{\"error\":\"Supabase update exception\"}");
                }
            } else {
                // update in-memory
                boolean updated = false;
                for (int i = 0; i < inMemory.size(); i++) {
                    String s = inMemory.get(i);
                    if (s.contains("\"id\":\"" + id + "\"")) {
                        // naive replacement of quantity
                        String replaced = s.replaceAll("(\"quantity\"\\s*:\\s*)[0-9]+", "$1" + quantity);
                        inMemory.set(i, replaced);
                        updated = true;
                        break;
                    }
                }
                if (updated) sendJson(exchange, 200, "{\"success\":true}");
                else sendJson(exchange, 404, "{\"error\":\"Not found\"}");
            }
        }

        private void addCors(Headers headers) {
            headers.add("Access-Control-Allow-Origin", "*");
            headers.add("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
            headers.add("Access-Control-Allow-Headers", "Content-Type,Authorization");
        }

        private void sendJson(HttpExchange exchange, int status, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }

        private String readStream(InputStream is) throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[2048];
            int r;
            while ((r = is.read(buf)) != -1) baos.write(buf, 0, r);
            return new String(baos.toByteArray(), StandardCharsets.UTF_8);
        }

        // Very small JSON extractors for controlled inputs
        private String getJsonString(String body, String key) {
            String pattern = "\"" + key + "\"\\s*:\\s*\\\"([^\\\"]*)\\\"";
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern).matcher(body);
            return m.find() ? m.group(1) : "";
        }

        private int getJsonInt(String body, String key, int def) {
            String pattern = "\"" + key + "\"\\s*:\\s*([0-9]+)";
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern).matcher(body);
            return m.find() ? Integer.parseInt(m.group(1)) : def;
        }

        private double getJsonDouble(String body, String key, double def) {
            String pattern = "\"" + key + "\"\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)";
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern).matcher(body);
            return m.find() ? Double.parseDouble(m.group(1)) : def;
        }

        private String esc(String s) { return s == null ? "" : s.replace("\"", "\\\""); }
    }

    public static void main(String[] args) throws Exception {
        // Use built-in Supabase credentials
        String supabaseUrl = "https://zprmdczzcvzpxibhtezp.supabase.co";
        String supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inpwcm1kY3p6Y3Z6cHhpYmh0ZXpwIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjEzNzYxMTMsImV4cCI6MjA3Njk1MjExM30.UUW7vcUFBv_Wcs1xbtDYwvE9ufgd4YKwQfbEj7m3zvs";
        
        // Override with environment variables if set
        if (System.getenv("SUPABASE_URL") != null) supabaseUrl = System.getenv("SUPABASE_URL");
        if (System.getenv("SUPABASE_SERVICE_KEY") != null) supabaseKey = System.getenv("SUPABASE_SERVICE_KEY");
        
        SupabaseClient supabase = new SupabaseClient(supabaseUrl, supabaseKey, "medicines");
        System.out.println("Supabase configured successfully.");
        MedicineApiServer server = new MedicineApiServer(supabase);
        server.start();
    }
}
