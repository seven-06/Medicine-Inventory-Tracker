import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Minimal Supabase REST client using Java HttpClient.
 * This client uses the REST endpoint (PostgREST) at {SUPABASE_URL}/rest/v1/{table}
 * Provide a service role key via environment variable when making server-side requests.
 */
public class SupabaseClient {
    private final String base;
    private final String apiKey; // service role key for server-side writes
    private final HttpClient http;
    private final String table;

    public SupabaseClient(String supabaseUrl, String apiKey, String table) {
        this.base = supabaseUrl.endsWith("/") ? supabaseUrl + "rest/v1" : supabaseUrl + "/rest/v1";
        this.apiKey = apiKey;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.table = table != null ? table : "medicines";
    }

    public String getAllMedicinesRaw() throws IOException, InterruptedException {
        String url = String.format("%s/%s?select=*", base, table);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("apikey", apiKey)
                .header("Authorization", "Bearer " + apiKey)
                .GET()
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() >= 200 && res.statusCode() < 300) return res.body();
        throw new IOException("Failed to fetch: " + res.statusCode() + " " + res.body());
    }

    /** Delete a row by id (uuid or numeric) */
    public boolean deleteById(String id) throws IOException, InterruptedException {
        String url = String.format("%s/%s?id=eq.%s", base, table, id);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("apikey", apiKey)
                .header("Authorization", "Bearer " + apiKey)
                .DELETE()
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        return res.statusCode() >= 200 && res.statusCode() < 300;
    }

    /** Update quantity for a row by id */
    public boolean updateQuantityById(String id, int quantity) throws IOException, InterruptedException {
        String url = String.format("%s/%s?id=eq.%s", base, table, id);
        String json = String.format("{\"quantity\":%d}", quantity);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("apikey", apiKey)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Prefer", "return=representation")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        return res.statusCode() >= 200 && res.statusCode() < 300;
    }

    /**
     * Add a medicine record. Note: server-side code should use a service role key for writes.
     */
    public boolean addMedicine(String name, int quantity, String expiryIso, double price, int minStock) throws IOException, InterruptedException {
    String url = String.format("%s/%s", base, table);
    String json = String.format("[{\\\"name\\\":\\\"%s\\\",\\\"quantity\\\":%d,\\\"expiry\\\":\\\"%s\\\",\\\"price\\\":%s,\\\"min_stock\\\":%d}]",
        escapeJson(name), quantity, escapeJson(expiryIso), Double.toString(price), minStock);
    HttpRequest req = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .timeout(Duration.ofSeconds(10))
        .header("apikey", apiKey)
        .header("Authorization", "Bearer " + apiKey)
        .header("Content-Type", "application/json")
        .header("Prefer", "return=representation")
        .POST(HttpRequest.BodyPublishers.ofString(json))
        .build();
    HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
    return res.statusCode() >= 200 && res.statusCode() < 300;
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
