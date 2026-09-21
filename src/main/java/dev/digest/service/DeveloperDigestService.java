package dev.digest.service;

import dev.digest.config.DigestConfig;
import dev.digest.infrai.InfraiCronClient;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class DeveloperDigestService {
    public record BuildEvent(String project, String revision, boolean passed, Instant occurredAt) {}
    public record ReleaseOperation(String project, String version, Instant releasedAt) {}
    public record Diagnostic(String service, String summary, Severity severity, Instant observedAt) {}
    public enum Severity { INFO, WARNING, CRITICAL }
    public record Digest(String subject, List<String> lines) {}

    private final DigestConfig config;
    private final InfraiCronClient cron;
    private final Clock clock;

    public DeveloperDigestService(DigestConfig config, InfraiCronClient cron, Clock clock) {
        this.config = config;
        this.cron = cron;
        this.clock = clock;
    }

    public String registerSchedule() throws IOException, InterruptedException {
        String stableInput = config.cronExpression() + "|" + config.digestTaskUri();
        return cron.create(config.cronExpression(), config.digestTaskUri(), UUID.nameUUIDFromBytes(stableInput.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    public Digest decide(List<BuildEvent> builds, List<ReleaseOperation> releases, List<Diagnostic> diagnostics) {
        LocalDate today = LocalDate.now(clock.withZone(config.digestZone()));
        if (today.getDayOfWeek() != config.digestDay()) return new Digest("Developer digest skipped", List.of());

        Instant cutoff = today.minusDays(7).atStartOfDay(config.digestZone()).toInstant();
        List<String> lines = new ArrayList<>();
        builds.stream().filter(event -> !event.occurredAt().isBefore(cutoff))
                .sorted(Comparator.comparing(BuildEvent::occurredAt))
                .forEach(event -> lines.add("BUILD " + event.project() + " " + event.revision() + " " + (event.passed() ? "PASS" : "FAIL")));
        releases.stream().filter(event -> !event.releasedAt().isBefore(cutoff))
                .sorted(Comparator.comparing(ReleaseOperation::releasedAt))
                .forEach(event -> lines.add("RELEASE " + event.project() + " " + event.version()));
        diagnostics.stream().filter(event -> !event.observedAt().isBefore(cutoff) && event.severity() != Severity.INFO)
                .sorted(Comparator.comparing(Diagnostic::observedAt))
                .forEach(event -> lines.add("DIAGNOSTIC " + event.severity() + " " + event.service() + " " + event.summary()));
        return new Digest("Developer tools weekly digest - " + today, List.copyOf(lines));
    }
}
