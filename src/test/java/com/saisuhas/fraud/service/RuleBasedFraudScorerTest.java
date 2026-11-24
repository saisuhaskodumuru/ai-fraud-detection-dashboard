package com.saisuhas.fraud.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;

import com.saisuhas.fraud.model.RiskBand;
import com.saisuhas.fraud.model.ScoreResult;
import com.saisuhas.fraud.model.TransactionRequest;
import com.saisuhas.fraud.store.TransactionStore;

class RuleBasedFraudScorerTest {

  private static TransactionRequest txn(String id, String customer, String amount,
      String country, String device, String category, Instant ts) {
    return new TransactionRequest(id, customer, new BigDecimal(amount), "USD",
        "Test Merchant", category, country, device, ts);
  }

  @Test
  void highAmountAndUnusualCountryScoresHigherThanNormal() {
    TransactionStore store = new TransactionStore();
    RuleBasedFraudScorer scorer = new RuleBasedFraudScorer(store);
    Instant noon = Instant.parse("2026-11-24T12:00:00Z");

    // Baseline: a normal US transaction for this customer, stored first.
    store.add(com.saisuhas.fraud.model.ScoredTransaction.from(
        txn("T-BASE", "CUST-1", "120.00", "US", "DEV-1", "GROCERIES", noon),
        new ScoreResult(5, RiskBand.LOW, "LEGITIMATE", java.util.List.of("NO_RISK_RULES_FIRED (+0)"), "local-rules-v1")));

    ScoreResult normal = scorer.score(
        txn("T-NORMAL", "CUST-1", "80.00", "US", "DEV-1", "GROCERIES", noon.plus(1, ChronoUnit.HOURS)), store);
    ScoreResult suspicious = scorer.score(
        txn("T-SUSPICIOUS", "CUST-1", "12400.00", "NG", "DEV-UNKNOWN", "CRYPTO", noon.plus(2, ChronoUnit.HOURS)), store);

    assertTrue(suspicious.score() > normal.score(),
        "suspicious score " + suspicious.score() + " should exceed normal " + normal.score());
    assertTrue(suspicious.score() >= 75, "suspicious should be CRITICAL, got " + suspicious.score());
    assertEquals(RiskBand.CRITICAL, suspicious.riskBand());
    assertTrue(suspicious.contributingRules().stream().anyMatch(r -> r.contains("AMOUNT_OVER_10000")));
    assertTrue(suspicious.contributingRules().stream().anyMatch(r -> r.contains("UNUSUAL_COUNTRY")));
  }

  @Test
  void velocityRuleFiresAfterSeveralTransactionsInAnHour() {
    TransactionStore store = new TransactionStore();
    RuleBasedFraudScorer scorer = new RuleBasedFraudScorer(store);
    Instant base = Instant.parse("2026-11-24T12:00:00Z");

    // Four prior transactions for the same customer within the hour.
    for (int i = 0; i < 4; i++) {
      TransactionRequest prior = txn("T-PRIOR-" + i, "CUST-9", "50.00", "US", "DEV-1",
          "RETAIL", base.plus(i, ChronoUnit.MINUTES));
      store.add(com.saisuhas.fraud.model.ScoredTransaction.from(prior,
          new ScoreResult(5, RiskBand.LOW, "LEGITIMATE", java.util.List.of("X"), "local-rules-v1")));
    }

    ScoreResult result = scorer.score(
        txn("T-VELOCITY", "CUST-9", "50.00", "US", "DEV-1", "RETAIL", base.plus(10, ChronoUnit.MINUTES)), store);

    assertTrue(result.contributingRules().stream().anyMatch(r -> r.contains("VELOCITY")),
        "velocity rule should fire, rules were: " + result.contributingRules());
  }

  @Test
  void normalTransactionScoresLow() {
    TransactionStore store = new TransactionStore();
    RuleBasedFraudScorer scorer = new RuleBasedFraudScorer(store);
    ScoreResult result = scorer.score(
        txn("T-LOW", "CUST-2", "45.00", "US", "DEV-1", "GROCERIES",
            Instant.parse("2026-11-24T12:00:00Z")), store);
    assertEquals(RiskBand.LOW, result.riskBand());
    assertEquals("local-rules-v1", result.model());
  }
}
