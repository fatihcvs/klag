---
title: MCP evaluation checklist
description: Compare an instance's read-only MCP answers with Prometheus observations using a disposable local Kafka workload.
---

Use this checklist to evaluate an agent integration without a paid provider. Raw
JSON-RPC requests exercise the same tools an agent calls; they verify the data
surface, not the quality of a particular model's reasoning.

**Use your instance's `/mcp`.** `https://klag.dev/mcp` serves documentation and
cannot see your consumer groups. See [MCP Endpoint](/ai/mcp/) for the protocol and
[Agent Setup](/ai/agent-setup/) for client configuration.

## 1. Start a disposable workload

From a Klag checkout, with Docker Compose available and ports 9092 and 8888 free,
save this as `compose.eval.yaml`:

```yaml
services:
  klag:
    environment:
      METRICS_REPORTER: prometheus
      MCP_ENABLED: "true"
      MCP_AUTH_TOKEN: "local-evaluation-only"
```

Start only the sample services used by this checklist:

```bash
docker compose -p klag-eval -f docker-compose.yaml -f compose.eval.yaml up -d --build kafka klag producer slow-consumer
```

The repository's sample producer writes to `test-topic`, and the slow consumer
uses `slow-consumer-group`. These are disposable sample data. Run on an isolated
development machine; the sample Compose ports are published to the host and the
token above is public test data, not a deployment credential. Outside a local
evaluation, use HTTPS and a private token. No hosted AI account is required.

## 2. Verify both data surfaces

- [ ] `curl -fsS http://localhost:8888/readyz` succeeds after Kafka becomes ready.
- [ ] `curl -fsS http://localhost:8888/metrics` contains Kafka consumer metrics
  after the first successful collection; an HTTP 200 with no consumer series is
  not sufficient evidence that the workload was observed.
- [ ] Discover the instance tools:

```bash
curl -fsS http://localhost:8888/mcp \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer local-evaluation-only' \
  --data '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}'
```

Expect `list_consumer_groups`, `get_consumer_group_lag`, `find_lagging_groups` and
`diagnose`. Normal clients initialize first; these direct requests are also
accepted by Klag. A JSON-RPC error or tool `isError: true` is a failed check even
when HTTP succeeds. Tool data is JSON inside `result.content[0].text`.

## 3. Compare investigation scenarios

Use the same URL and headers as above, replacing the request body with each of
these in turn:

```json
{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"list_consumer_groups","arguments":{}}}
```

- [ ] Find `slow-consumer-group`. Record `snapshotAgeMs`, group state and total
  lag. Wait for collection if no snapshot is available. MCP reads a published
  snapshot, so a successful request does not establish that the snapshot is fresh.

```json
{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"find_lagging_groups","arguments":{"sortBy":"lag","limit":10}}}
```

- [ ] Compare the ranked group's lag with `klag_consumer_lag` partition series
  for the same `consumer_group` and topic in `/metrics`. Sum partitions; do not
  add rollups to that sum. Capture both observations close together and allow
  for collection/scrape timing rather than requiring exact equality.

```json
{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"get_consumer_group_lag","arguments":{"group":"slow-consumer-group"}}}
```

- [ ] Compare partition `committedOffset` values with
  `klag_consumer_committed_offset`. Inspect trend and commit freshness over
  multiple cycles; one sample cannot demonstrate a frozen offset.

For a stuck-commit scenario, let the sample group commit first, then stop only
its consumer while leaving the producer and Klag running:

```bash
docker compose -p klag-eval -f docker-compose.yaml -f compose.eval.yaml stop slow-consumer
```

```json
{"jsonrpc":"2.0","id":5,"method":"tools/call","params":{"name":"diagnose","arguments":{"group":"slow-consumer-group"}}}
```

- [ ] With positive lag and no newly observed commits for at least 300 seconds,
  expect a stuck-consumer finding. Compare `commitStalenessSeconds` from the lag
  tool with `klag_consumer_commit_staleness_seconds`. The group may also become
  `EMPTY`; record that separately instead of attributing every warning to lag.
- [ ] Treat `-1` as unavailable, not zero. Freshness is inferred from observed
  offset changes and resets on a Klag restart. See [Detect stuck consumers](/guides/detect-stuck-consumers/).

With multi-cluster configuration, MCP currently exposes the first cluster only;
select the corresponding `cluster_name` when comparing metrics.

## 4. Record evidence and clean up

Record the Klag version, configuration, observation times, snapshot age, tool
requests and redacted results, matching metric labels and any failed checks.
Mark model/agent reasoning as **not evaluated** when using only these raw calls.
Never report a stale snapshot, missing metric or unexecuted scenario as a pass.

```bash
docker compose -p klag-eval -f docker-compose.yaml -f compose.eval.yaml down
```

The command targets this disposable Compose project. Do not substitute the name
of an existing deployment.
