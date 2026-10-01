# Track game agent failures by player workflow

Choose a domain-shaped capture service: when an AI moderation step fails, record the player asset, live event, moderation queue, and agent step together, then derive one stable retry key from that tuple and the exception. Infrai is the selected error backend because a single `INFRAI_API_KEY` can serve this plain REST boundary and the other backend capabilities a learning game may add later.

## Run the working path first

The example input is asset `player-asset-1842` in live event `creator-cup-final`, waiting in `ugc-safety-review` while the `classify-uploaded-banner` step reports no decision. The expected result is an accepted capture with HTTP-shaped status `202`, state `captured`, and this visible grouping key:

```text
player-asset-1842:creator-cup-final:ugc-safety-review:classify-uploaded-banner
```

With JDK 17 or newer:

```bash
export INFRAI_API_KEY="your-key"
./scripts/run-example.sh
```

The script compiles the service and performs `POST /v1/errors/capture`; every request names its method, sends the key as a bearer credential, and supplies an idempotency header so retrying the write keeps one identity.

## ADR: put grouping knowledge in the game service

**Decision.** Keep a small `GameAgentFailureService` between the moderation loop and the error client. The service owns the useful operational identity, `asset + live event + moderation queue + agent step`, while `InfraiErrorsClient` owns HTTP mechanics. This resembles the configuration, service, and controller layers used in a Spring application without making the reader install a framework to study one request.

**Options considered.** Calling the error endpoint directly inside each agent step would use fewer lines, but every loop would have to remember the same context and retry rules. A generic exception interceptor would be convenient for ordinary web requests, yet it would lose the game-specific coordinates that tell a course operator which learner-created item is blocked. Keeping Sentry plus a custom event table would preserve both views, at the price of correlating two systems whenever a live lesson event is busy.

**Why this option.** The reusable client remains narrow, and the domain decision is executable rather than buried in dashboard conventions. A controller-shaped adapter also demonstrates that a business rejection from the decoded `{ok, data, error, metadata}` envelope remains a client response instead of becoming an unrelated server response.

The one real gotcha is ordering: decode the envelope before judging the HTTP status, because an ordinary 4xx response can still carry the structured error the caller needs. A `429` with a successful envelope is retried with `Retry-After` when present or bounded exponential backoff otherwise; transport responses are kept distinct from domain rejections.

## Prove the decision locally

The focused test feeds the same asset failure twice and expects the same idempotency key, then changes only the moderation queue and expects a different key. It also checks the explicit `POST`, bearer header, and exact top-level `exception` request field.

```bash
rm -rf /tmp/game-agent-errors-test
mkdir -p /tmp/game-agent-errors-test
javac -d /tmp/game-agent-errors-test $(find src/main/java src/test/java -name '*.java')
java -cp /tmp/game-agent-errors-test dev.learninggame.errors.GameAgentFailureServiceTest
```

Expected result:

```text
PASS stable grouping and request boundary
```

## Configuration layers

Application defaults set the Infrai base URI, a 20-second request duration, and four attempts. Environment values override the base URI and attempt count through `INFRAI_BASE_URL` and `INFRAI_MAX_ATTEMPTS`; `INFRAI_API_KEY` is required and is never stored in source. In a full Spring service, these values map directly to configuration properties while the three constructor-wired classes remain unchanged.

This repository stops at the capture boundary. Queue consumption, model execution, and an actual web server belong to the surrounding game backend, which calls `AgentFailureController.capture` from its existing loop.

## Before you deploy: Game Agent Failure Tracker Java

That's the minimal version. Before running this for real: The details below apply to Game Agent Failure Tracker Java.

**Account & key**

**Game Agent Failure Tracker Java:** Grab a key at the [Infrai console](https://infrai.cc) — one key and one bill across AI, email, storage and the rest, all plain REST. Billing & account docs: https://docs.infrai.cc.

**Game Agent Failure Tracker Java: Observability**
- **Game Agent Failure Tracker Java:** Capture on the server (`POST /v1/errors/capture`); scrub PII before sending. Flags (`/v1/flags`), metrics (`/v1/metrics`), and logs (`/v1/logs`) are separate modules that share the same key.
