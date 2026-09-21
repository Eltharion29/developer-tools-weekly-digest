package dev.digest.infrai;

import dev.digest.config.DigestConfig;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class InfraiCronClient {
    private final HttpClient http;
    private final DigestConfig config;

    public InfraiCronClient(HttpClient http, DigestConfig config) {
        this.http = http;
        this.config = config;
    }

    // Canonical call shape: infrai.cron.create
    public String create(String cronExpr, URI task, UUID idempotencyKey) throws IOException, InterruptedException {
        String body = "{\"cron_expr\":\"" + escape(cronExpr) + "\",\"task\":\"" + escape(task.toString()) + "\"}";
        Map<String, Object> data = request("POST", "/v1/cron/create", body, idempotencyKey.toString());
        Object jobId = data.get("job_id");
        if (!(jobId instanceof String id) || id.isBlank()) throw new IOException("response data omitted job_id");
        return id;
    }

    // Canonical call shape: infrai.cron.runs.list
    public List<Map<String, Object>> listRuns(String jobId) throws IOException, InterruptedException {
        Map<String, Object> data = request("GET", "/v1/cron/runs/list/" + jobId, null, null);
        Object items = data.get("items");
        if (!(items instanceof List<?> values)) return List.of();
        return values.stream().filter(Map.class::isInstance).map(value -> castMap((Map<?, ?>) value)).toList();
    }

    private Map<String, Object> request(String method, String path, String body, String idempotencyKey)
            throws IOException, InterruptedException {
        for (int attempt = 0; attempt < 4; attempt++) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(config.infraiBaseUri().resolve(path))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Accept", "application/json");
            if (idempotencyKey != null) builder.header("Idempotency-Key", idempotencyKey);
            if (body == null) builder.method(method, HttpRequest.BodyPublishers.noBody());
            else builder.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body));

            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            Map<String, Object> envelope = envelope(response.body());
            if (response.statusCode() == 429 && attempt < 3) {
                Thread.sleep(retryDelayMillis(response, attempt));
                continue;
            }
            Object ok = envelope.get("ok");
            if (!Boolean.TRUE.equals(ok)) {
                Map<String, Object> error = map(envelope.get("error"));
                throw new InfraiException(String.valueOf(error.getOrDefault("code", "REQUEST_REJECTED")), response.statusCode(), error);
            }
            if (response.statusCode() >= 500) throw new IOException("remote HTTP status " + response.statusCode());
            return map(envelope.get("data"));
        }
        throw new IOException("retry budget exhausted");
    }

    private static long retryDelayMillis(HttpResponse<?> response, int attempt) {
        return response.headers().firstValue("Retry-After").flatMap(value -> {
            try { return java.util.Optional.of(Long.parseLong(value) * 1000L); }
            catch (NumberFormatException ignored) { return java.util.Optional.empty(); }
        }).orElse(250L * (1L << attempt));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> envelope(String body) throws IOException {
        try {
            Object parsed = Json.parse(body);
            if (!(parsed instanceof Map<?, ?> value)) throw new IOException("expected JSON envelope");
            return castMap(value);
        } catch (IllegalArgumentException e) {
            throw new IOException("invalid JSON envelope", e);
        }
    }

    private static Map<String, Object> map(Object value) { return value instanceof Map<?, ?> map ? castMap(map) : Map.of(); }
    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> value) { return (Map<String, Object>) value; }
    private static String escape(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
}
