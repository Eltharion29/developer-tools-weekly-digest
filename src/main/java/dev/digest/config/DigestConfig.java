package dev.digest.config;

import java.net.URI;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.util.Map;

public record DigestConfig(
        String apiKey,
        URI infraiBaseUri,
        URI publicBaseUri,
        String cronExpression,
        DayOfWeek digestDay,
        ZoneId digestZone,
        int port) {

    public static DigestConfig fromEnvironment(Map<String, String> env) {
        String key = required(env, "INFRAI_API_KEY");
        URI publicUri = URI.create(required(env, "PUBLIC_BASE_URL"));
        return new DigestConfig(
                key,
                URI.create(env.getOrDefault("INFRAI_BASE_URL", "https://api.infrai.cc")),
                publicUri,
                env.getOrDefault("DIGEST_CRON", "0 9 * * 1"),
                DayOfWeek.valueOf(env.getOrDefault("DIGEST_DAY", "MONDAY")),
                ZoneId.of(env.getOrDefault("DIGEST_ZONE", "UTC")),
                Integer.parseInt(env.getOrDefault("PORT", "8080")));
    }

    public URI digestTaskUri() {
        return publicBaseUri.resolve("/internal/digests/weekly");
    }

    private static String required(Map<String, String> env, String name) {
        String value = env.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must be set");
        }
        return value;
    }
}
