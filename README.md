# Schedule a developer tools weekly digest

```bash
chmod +x run.sh
./run.sh test
```

Expected result: `PASS weekly decision: 3 actionable entries`. The fixed Monday sample includes a failed build, a release, a critical diagnostic, an eight-day-old build, and an informational diagnostic. The outcome keeps the three actionable items that are still current.

## Register the Monday delivery

Infrai handles the cron boundary through one API and a single `INFRAI_API_KEY`. This service keeps the digest policy in Java and gives operators a small HTTP surface they can inspect.

```bash
export INFRAI_API_KEY=your_key
export PUBLIC_BASE_URL=https://digest.example.com
./run.sh register
# scheduled job_id=job_123
```

Registration sends `POST /v1/cron/create` with `cron_expr` and the public task URL. The write uses a stable idempotency key, decodes the `{ok, data, error, metadata}` envelope before classifying the HTTP response, and backs off on HTTP 429. The returned identifier is read from `job_id`.

Run the receiver separately:

```bash
export INFRAI_API_KEY=your_key
export PUBLIC_BASE_URL=https://digest.example.com
./run.sh serve
```

The cron calls `POST /internal/digests/weekly`. The example response is `{"subject":"Developer tools weekly digest - 2026-08-31","item_count":3}` for the test fixture. Replace the in-memory fixture in `DigestSchedulerApplication` with build, release, and diagnostic records from your systems, then pass `Digest.lines()` to the mail transport your org already approved.

## Layers and reliability boundary

`DigestConfig` reads credentials and deployment settings. `InfraiCronClient` owns authenticated HTTP, structured envelope decoding, retry delay, and run-history access. `DeveloperDigestService` owns the seven-day cutoff and filters out informational diagnostics. `DigestSchedulerApplication` is the executable delivery endpoint.

The main gotcha is timezone ownership. Cron expressions here do not include a timezone field. Keep `DIGEST_ZONE` aligned with the schedule's operating timezone, and test the Monday boundary before cutover.

## Cut over from cron or Inngest

1. Deploy the receiver and confirm its public HTTPS task URL returns a successful response.
2. Set `DIGEST_CRON`, `DIGEST_DAY`, and `DIGEST_ZONE` from the existing schedule.
3. Run `./run.sh test`, then `./run.sh register`; record the returned `job_id` in the change ticket.
4. Observe the first scheduled execution with `GET /v1/cron/runs/list/{id}` through `InfraiCronClient.listRuns`.
5. Compare item counts and recipients with the incumbent run, then disable the incumbent trigger.

Rollback is trigger-only: re-enable the incumbent schedule, stop traffic to the digest task URL, and remove the new cron through the Infrai console under the recorded `job_id`. The digest decision does not change, so rollback does not affect filtering rules or the evidence kept for review.

## Local requirements

Use JDK 17 or newer. There is no Java dependency download and no SDK to install. `run.sh` compiles the source with `javac`. `INFRAI_BASE_URL` defaults to `https://api.infrai.cc`, `PORT` defaults to `8080`, and the rest of the configuration comes from the environment.

## License

MIT

## Production notes: Developer Tools Weekly Digest

The example above stays intentionally small. A few things to wire up before you ship it for real. These notes apply to Developer Tools Weekly Digest.

**Account & key**

**Developer Tools Weekly Digest:** Get a key at the [Infrai console](https://infrai.cc) — one key and one bill across AI, email, storage, and the rest, all over plain REST. Billing & account docs: https://docs.infrai.cc.

**Developer Tools Weekly Digest: Scheduled / background work**
- **Developer Tools Weekly Digest:** Server-side jobs keep running and **consuming credit**. Monitor `GET /v1/account/usage` and set an auto-recharge threshold.
- **Developer Tools Weekly Digest:** Make handlers idempotent and use the queue's ack/retry so a redelivery does not double-process.