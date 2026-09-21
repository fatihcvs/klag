# Contributing to Klag

Thanks for helping. Full guide: [klag.dev/development/contributing](https://klag.dev/development/contributing/).

## Quick start

1. **Pick an issue** before opening a PR — prefer ones labeled [`good first issue`](https://github.com/themoah/klag/issues?q=is%3Aissue+is%3Aopen+label%3A%22good+first+issue%22) or [`hacktoberfest`](https://github.com/themoah/klag/issues?q=is%3Aissue+is%3Aopen+label%3Ahacktoberfest). Comment on the issue to claim it.
2. Fork, branch from `main`, make a focused change.
3. Requires **Java 21**. Run:
   ```bash
   ./gradlew test
   ```
   Helm chart changes also need `./scripts/test-helm-chart.sh`. Website/docs changes: `cd website && npm ci && npm test && npm run check`.
4. Open a pull request that links the issue.

## Quality bar

- Prefer a linked issue and tests for behavior changes.
- **Drive-by typo / whitespace-only PRs will be closed** unless they fix something substantive or were agreed on an issue.
- Metric names and tags are a public API — see the [contributing guide](https://klag.dev/development/contributing/) when adding or retagging metrics.

## Hacktoberfest

Look for the [`hacktoberfest`](https://github.com/themoah/klag/issues?q=is%3Aissue+is%3Aopen+label%3Ahacktoberfest) label and the pinned contributor-board issue. Issues are curated; unsolicited spam PRs will not be merged.
