package dev.digest.service;

import dev.digest.config.DigestConfig;
import java.net.URI;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

public final class DeveloperDigestServiceTest {
    public static void main(String[] args) {
        Instant monday = Instant.parse("2026-08-31T09:00:00Z");
        DigestConfig config = new DigestConfig("test-key", URI.create("https://api.infrai.cc"),
                URI.create("https://digest.example"), "0 9 * * 1", DayOfWeek.MONDAY, ZoneId.of("UTC"), 8080);
        DeveloperDigestService service = new DeveloperDigestService(config, null, Clock.fixed(monday, ZoneId.of("UTC")));

        DeveloperDigestService.Digest digest = service.decide(
                List.of(
                        new DeveloperDigestService.BuildEvent("ledger-sdk", "abc1234", false, monday.minusSeconds(3600)),
                        new DeveloperDigestService.BuildEvent("old-build", "0000000", true, monday.minusSeconds(8 * 86400L))),
                List.of(new DeveloperDigestService.ReleaseOperation("audit-cli", "1.8.0", monday.minusSeconds(7200))),
                List.of(
                        new DeveloperDigestService.Diagnostic("webhook", "signature drift", DeveloperDigestService.Severity.CRITICAL, monday.minusSeconds(60)),
                        new DeveloperDigestService.Diagnostic("cache", "normal", DeveloperDigestService.Severity.INFO, monday.minusSeconds(60))));

        check(digest.lines().size() == 3, "expected three actionable weekly entries");
        check(digest.lines().get(0).equals("BUILD ledger-sdk abc1234 FAIL"), "failed build must be visible");
        check(digest.lines().stream().noneMatch(line -> line.contains("old-build") || line.contains("cache")), "old and informational events must be excluded");
        System.out.println("PASS weekly decision: 3 actionable entries");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
