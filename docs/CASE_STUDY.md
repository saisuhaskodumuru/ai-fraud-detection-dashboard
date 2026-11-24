# Case Study: AI-Powered Fraud Detection Dashboard

## What this is

This is a personal project built to demonstrate fraud detection, as a
standalone portfolio project. It is not associated with any work
experience, and it contains no real customer data. Every transaction in
it is synthetic. The goal was to show the same shape of system in a form
anyone can run locally:
a Spring Boot service that scores payment transactions in real time, a
rule-based scorer that runs when no ML endpoint is configured, an Azure ML
client that can take over scoring when an endpoint is configured, and a
dashboard that makes the risk visible.

## Problem

Fraud teams need a score for every transaction within milliseconds of it
arriving, with a reason they can read, not just a number. A black-box score
does not help an analyst decide whether to block a payment, and a system
that only works when the ML endpoint is up does not survive an outage.
Production fraud systems face the same constraints at scale: high
transaction volume, a fraud model served from Azure ML, and a requirement
that scoring never block the payment flow.

## My role

I built this personal project end to end: the scoring service, the rule
engine, the Azure ML integration point, the in-memory store, the REST
API, the dashboard UI, and the tests.

## Architecture

```
Browser dashboard (static HTML/CSS/JS)
        |
        |  REST / JSON
        v
FraudController  --  POST /api/v1/transactions/score
                     GET  /api/v1/transactions?risk=HIGH
                     GET  /api/v1/dashboard/stats
                     GET  /api/v1/customers/{id}/classification
        |
        v
FraudScoringService
        |                       |
        | Azure ML configured?  | no, or Azure ML fails
        v                       v
AzureMlFraudScoringClient   RuleBasedFraudScorer
(Spring RestClient)          (deterministic rules)
        |                       |
        +-----------+-----------+
                    v
            TransactionStore (in-memory)
                    ^
                    |
            SeedDataGenerator (200 synthetic
            transactions plus planted fraud
            patterns, on startup)
```

Scoring tries Azure ML first when `AZURE_ML_ENDPOINT` is set. Any failure,
or no endpoint at all, falls back to the local rule scorer, and the response
says which model produced the score: `azure-ml` or `local-rules-v1`. The
local scorer is deterministic: amount thresholds, amount versus the
customer's own baseline, velocity in the last hour, unusual country and
device, merchant category risk, and night-time transactions. Each fired
rule is listed in the response with its point contribution, so the score
is explainable. The app runs on Java 21 virtual threads
(`spring.threads.virtual.enabled=true`).

## What was built

- A Spring Boot 3.3, Java 21, Maven service with no database. All state is
  in memory, which keeps the demo self-contained.
- Four endpoints: score a transaction, list scored transactions with an
  optional risk filter, dashboard statistics, and a per-customer risk
  summary.
- A deterministic rule-based scorer with seven rule families and explicit
  point weights, plus an Azure ML client interface using Spring RestClient,
  configured only by the `AZURE_ML_ENDPOINT` and `AZURE_ML_API_KEY`
  environment variables. The API key is sent only as an Authorization
  header to the configured endpoint and is never logged.
- A seed generator that loads 200 synthetic transactions on startup with
  a fixed random seed, plus 9 planted fraud patterns: two high-value
  foreign crypto transactions, one night-time gambling transaction, and
  a six-transaction velocity burst. The demo is reproducible on every run.
- A static dashboard UI with no build step and no external JavaScript: a
  score-a-transaction form, live statistics, a risk-band breakdown, and a
  transactions table with risk badges. It calls the real APIs.
- Tests: 3 JUnit 5 unit tests for the scoring service and 2 MockMvc tests
  for the score endpoint, including a validation failure case.

## Results, measured on this demo

These numbers were measured locally on 2026-11-24 by running the packaged
application and curling the real endpoints. They describe the demo only.

- Tests: 5 run, 0 failures, 0 errors. The scorer tests ran in 0.060 s and
  the controller tests in 6.213 s (mostly Spring context startup).
- Application startup: 4.403 s from launch to "Started
  FraudDashboardApplication".
- Seeded dashboard at startup: 209 transactions, fraud rate 7.66 percent,
  average score 24.88. Risk bands: LOW 134, MEDIUM 59, HIGH 11,
  CRITICAL 5.
- `GET /api/v1/dashboard/stats`: HTTP 200 in 0.363 s on the first hit.
- `POST /api/v1/transactions/score`, a $12,400 crypto transaction from NG
  at 02:00 UTC on a new device: HTTP 201 in 0.648 s on the first hit,
  score 75, CRITICAL, classification FRAUD_SUSPECT, model
  `local-rules-v1`, with four contributing rules listed.
- `POST /api/v1/transactions/score`, a $42.50 Starbucks purchase in the
  US: HTTP 201 in 0.006 s, score 15, LOW, classification LEGITIMATE.
- `GET /api/v1/transactions?risk=HIGH`: HTTP 200, returns only HIGH band
  transactions.
- `GET /api/v1/customers/CUST-009/classification`: HTTP 200, 10
  transactions, average score 31.5, max score 60, 3 flagged, MEDIUM,
  classification REVIEW.
- Dashboard UI at `http://localhost:8080/`: HTTP 200 in 0.115 s,
  7,471 bytes of static HTML, CSS, and JavaScript.

No production metrics are claimed here. All numbers above describe this
demo only.

## Challenges

- The sandbox blocked Java's outbound TCP, so Maven could not download
  dependencies from Maven Central at all, even though curl could reach it
  through the egress proxy. I resolved dependencies with a Python script
  that downloaded poms and jars through curl into the local Maven
  repository, then built offline with `mvn -o`, fetching the remaining
  missing artifacts one at a time as Maven reported them. The full
  dependency tree for a Spring Boot web app plus its test and plugin
  dependencies is large: the local repository grew to roughly 900 jars
  before the build passed.
- The Spring context failed to load in the controller tests because the
  Azure ML client had two constructors and Spring could not pick the
  autowire candidate. Annotating the primary constructor with
  `@Autowired` fixed it.
- A controller test expected CRITICAL for a transaction that scored 65
  under the documented rule weights, which maps to HIGH. Rather than
  changing the scorer to fit the test, I changed the test transaction to
  02:00 UTC so the night-time rule fires and the score reaches 75, which
  is the real CRITICAL threshold.

## Tech stack

Java 21 (virtual threads), Spring Boot 3.3, Spring MVC, Spring Validation,
Jackson, JUnit 5, MockMvc, Maven, Azure ML (client interface, configured
by environment variables), vanilla HTML/CSS/JS dashboard. No database.
