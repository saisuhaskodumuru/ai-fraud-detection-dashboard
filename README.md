# AI-Powered Fraud Detection Dashboard

A real working demo that scores payment transactions for fraud in real time.
It is a personal project built by Sai Suhas Kodumuru to demonstrate
real-time fraud scoring with Spring Boot microservices and Azure ML.
It is not associated with any work experience, and it contains no real
customer data. Every transaction in it is synthetic.

## What it does

- Scores a transaction 0-100 with a risk band (LOW, MEDIUM, HIGH, CRITICAL),
  a classification, the rules that fired, and the model that scored it.
- Lists scored transactions, with an optional risk filter.
- Shows dashboard stats: totals, fraud rate, average score, counts by risk
  band, and top risky merchants.
- Summarises a customer: transaction count, average and max score, fraud
  count, and risk band.
- Serves a dashboard UI at the root URL that calls the real APIs.

## Architecture

```
Browser / curl
      |
      v
Static dashboard (src/main/resources/static/index.html)
      |
      v
Spring Boot 3.3, Java 21, virtual threads
  FraudController  (REST, /api/v1/...)
      |
      +--> FraudScoringService
      |       |
      |       +--> AzureMlFraudScoringClient (RestClient, only when
      |       |     AZURE_ML_ENDPOINT is set; on any failure it falls
      |       |     back to the local scorer)
      |       |
      |       +--> RuleBasedFraudScorer (local-rules-v1, deterministic)
      |
      +--> TransactionStore (in-memory, CopyOnWriteArrayList)
      |
      +--> SeedDataGenerator (200 synthetic transactions on startup,
              plus planted fraud patterns)
```

No database. No persistence. All data lives only for the life of the
process. Java 21 virtual threads are enabled with
`spring.threads.virtual.enabled=true`.

## Scoring rules (local-rules-v1)

The local scorer is deterministic: the same transaction always gets the
same score. Rules, with points:

- Amount over 1000 (+15), over 5000 (+25), over 10000 (+30)
- Amount over 3x the customer's own baseline (+20)
- Velocity: 3 or more transactions in the last hour (+15), 5 or more (+25)
- Unusual country for the customer (+20), high-risk country (+10)
- Unusual device for the customer (+15)
- High-risk merchant category, gambling or crypto (+25); electronics or
  travel (+10)
- Night-time transaction, 00:00-04:59 UTC (+10)

Risk bands: 0-29 LOW, 30-54 MEDIUM, 55-74 HIGH, 75-100 CRITICAL.
Classification: LOW is LEGITIMATE, MEDIUM is REVIEW, HIGH and CRITICAL
are FRAUD_SUSPECT.

## How to run

Requirements: Java 21 and Maven 3.9 or later.

```bash
mvn spring-boot:run
```

Then open http://localhost:8080 in a browser.

Or build and run the jar:

```bash
mvn package
java -jar target/ai-fraud-detection-dashboard-1.0.0.jar
```

## API examples

Score a transaction (a high-value foreign crypto transaction, which
should score CRITICAL):

```bash
curl -s -X POST http://localhost:8080/api/v1/transactions/score \
  -H 'Content-Type: application/json' \
  -d '{
    "id": "TXN-1",
    "customerId": "CUST-001",
    "amount": 12400.00,
    "currency": "USD",
    "merchant": "Crypto Exchange X",
    "merchantCategory": "CRYPTO",
    "country": "NG",
    "deviceId": "DEV-UNKNOWN",
    "timestamp": "2026-11-24T12:00:00Z"
  }'
```

List transactions, optionally filtered by risk:

```bash
curl -s http://localhost:8080/api/v1/transactions
curl -s "http://localhost:8080/api/v1/transactions?risk=HIGH"
```

Dashboard stats:

```bash
curl -s http://localhost:8080/api/v1/dashboard/stats
```

Customer classification:

```bash
curl -s http://localhost:8080/api/v1/customers/CUST-007/classification
```

## How Azure ML would plug in

The scoring service tries Azure ML first, and only when an endpoint is
configured. Set these environment variables:

```bash
export AZURE_ML_ENDPOINT="https://<workspace>.azureml.net/.../score"
export AZURE_ML_API_KEY="<key>"
mvn spring-boot:run
```

The Azure ML client (`AzureMlFraudScoringClient`) posts the transaction
as JSON to that endpoint and expects a JSON response with a numeric
`score` field from 0 to 100. When the endpoint returns a score, the
response says `"model": "azure-ml"`. When no endpoint is configured, or
the call fails for any reason, the service falls back to the local rule
scorer and the response says `"model": "local-rules-v1"`. The API key is
sent only as an Authorization header to the configured endpoint and is
never logged.

This project follows a common production pattern: Spring Boot microservices
call an ML model in real time and return scores over REST, with monitoring
for drift and slow calls. This demo keeps that shape, with a local scorer
standing in for the model when no endpoint is present.

## Tests

```bash
mvn test
```

Tests cover the scoring service (a high amount plus an unusual country
scores higher than a normal transaction, and the velocity rule fires)
and the score endpoint (a valid request returns 201 with a CRITICAL
band, an invalid amount returns 400).

## Data

All data is synthetic. The seed generator creates 200 transactions on
startup from a fixed random seed, plus a few planted fraud patterns
(high-value foreign crypto transactions, a night-time gambling
transaction, and a velocity burst), so the dashboard is not empty.
No real customer, merchant, or transaction data is used.
