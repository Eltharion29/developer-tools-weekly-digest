package dev.digest;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.digest.config.DigestConfig;
import dev.digest.infrai.InfraiCronClient;
import dev.digest.infrai.InfraiException;
import dev.digest.service.DeveloperDigestService;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

public final class DigestSchedulerApplication {
    public static void main(String[] args) throws Exception {
        DigestConfig config = DigestConfig.fromEnvironment(System.getenv());
        InfraiCronClient client = new InfraiCronClient(HttpClient.newHttpClient(), config);
        DeveloperDigestService service = new DeveloperDigestService(config, client, Clock.systemUTC());
        if (args.length == 1 && args[0].equals("register")) {
            System.out.println("scheduled job_id=" + service.registerSchedule());
            return;
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(config.port()), 0);
        server.createContext("/internal/digests/weekly", exchange -> handleDigest(exchange, service));
        server.start();
        System.out.println("digest receiver listening on port " + config.port());
    }

    private static void handleDigest(HttpExchange exchange, DeveloperDigestService service) throws IOException {
        if (!exchange.getRequestMethod().equals("POST")) { send(exchange, 405, "{\"error\":\"method_not_allowed\"}"); return; }
        try {
            Instant now = Instant.now();
            DeveloperDigestService.Digest digest = service.decide(
                    List.of(new DeveloperDigestService.BuildEvent("payments-cli", "a91c2e1", true, now.minusSeconds(3600))),
                    List.of(new DeveloperDigestService.ReleaseOperation("schema-linter", "2.4.0", now.minusSeconds(1800))),
                    List.of(new DeveloperDigestService.Diagnostic("artifact-index", "p95 latency above review threshold", DeveloperDigestService.Severity.WARNING, now.minusSeconds(900))));
            String json = "{\"subject\":\"" + escape(digest.subject()) + "\",\"item_count\":" + digest.lines().size() + "}";
            send(exchange, 200, json);
        } catch (InfraiException e) {
            int status = e.status() >= 400 && e.status() < 500 ? e.status() : 502;
            send(exchange, status, "{\"error\":\"" + escape(e.code()) + "\"}");
        }
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
    }

    private static String escape(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
}
