package com.saisuhas.fraud.service;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.saisuhas.fraud.model.RiskBand;
import com.saisuhas.fraud.model.ScoreResult;
import com.saisuhas.fraud.model.TransactionRequest;
import com.saisuhas.fraud.store.TransactionStore;

/**
 * Deterministic local scorer. Every rule is explicit and reproducible, so
 * the same transaction always gets the same score. Used on its own when no
 * Azure ML endpoint is configured, and as the fallback when Azure ML fails.
 */
@Component
public class RuleBasedFraudScorer implements FraudScoringClient {

  public static final String MODEL = "local-rules-v1";

  private final TransactionStore store;

  public RuleBasedFraudScorer(TransactionStore store) {
    this.store = store;
  }

  @Override
  public Optional<ScoreResult> score(TransactionRequest request) {
    return Optional.of(score(request, store));
  }

  public ScoreResult score(TransactionRequest request, TransactionStore history) {
    List<String> rules = new ArrayList<>();
    int score = 0;

    // Amount rules: absolute thresholds.
    if (request.amount().compareTo(new BigDecimal("10000")) >= 0) {
      score += 30;
      rules.add("AMOUNT_OVER_10000 (+30)");
    } else if (request.amount().compareTo(new BigDecimal("5000")) >= 0) {
      score += 25;
      rules.add("AMOUNT_OVER_5000 (+25)");
    } else if (request.amount().compareTo(new BigDecimal("1000")) >= 0) {
      score += 15;
      rules.add("AMOUNT_OVER_1000 (+15)");
    }

    // Amount vs the customer's own baseline (average of prior transactions).
    double baseline = history.averageAmountFor(request.customerId());
    if (baseline > 0 && request.amount().doubleValue() > 3 * baseline) {
      score += 20;
      rules.add("AMOUNT_OVER_3X_CUSTOMER_BASELINE (+20)");
    }

    // Velocity: many transactions from the same customer in the last hour.
    long recent = history.countForCustomerSince(request.customerId(),
        request.timestamp().minusSeconds(3600));
    if (recent >= 5) {
      score += 25;
      rules.add("VELOCITY_5_PLUS_IN_LAST_HOUR (+25)");
    } else if (recent >= 3) {
      score += 15;
      rules.add("VELOCITY_3_PLUS_IN_LAST_HOUR (+15)");
    }

    // Unusual country: the customer has only transacted in the US before.
    boolean seenCountry = history.hasCustomerSeenCountry(request.customerId(), request.country());
    if (history.hasCustomerHistory(request.customerId()) && !seenCountry
        && !"US".equalsIgnoreCase(request.country())) {
      score += 20;
      rules.add("UNUSUAL_COUNTRY (+20)");
    } else if (!"US".equalsIgnoreCase(request.country())
        && !"CA".equalsIgnoreCase(request.country())
        && !"GB".equalsIgnoreCase(request.country())) {
      score += 10;
      rules.add("HIGH_RISK_COUNTRY (+10)");
    }

    // Unusual device: a device the customer has never used before.
    if (history.hasCustomerHistory(request.customerId())
        && !history.hasCustomerSeenDevice(request.customerId(), request.deviceId())) {
      score += 15;
      rules.add("UNUSUAL_DEVICE (+15)");
    }

    // Merchant category risk.
    String category = request.merchantCategory().toUpperCase();
    if (category.contains("GAMBLING") || category.contains("CRYPTO")) {
      score += 25;
      rules.add("HIGH_RISK_MERCHANT_CATEGORY (+25)");
    } else if (category.contains("ELECTRONICS") || category.contains("TRAVEL")) {
      score += 10;
      rules.add("ELEVATED_MERCHANT_CATEGORY (+10)");
    }

    // Night-time transactions, 00:00-04:59 UTC.
    int hour = request.timestamp().atZone(ZoneOffset.UTC).getHour();
    if (hour < 5) {
      score += 10;
      rules.add("NIGHT_TIME_TRANSACTION (+10)");
    }

    int clamped = Math.min(100, Math.max(0, score));
    if (rules.isEmpty()) {
      rules.add("NO_RISK_RULES_FIRED (+0)");
    }
    RiskBand band = RiskBand.fromScore(clamped);
    return new ScoreResult(clamped, band, band.classification(), List.copyOf(rules), MODEL);
  }
}
