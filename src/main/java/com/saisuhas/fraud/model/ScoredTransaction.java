package com.saisuhas.fraud.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ScoredTransaction(
    String id,
    String customerId,
    BigDecimal amount,
    String currency,
    String merchant,
    String merchantCategory,
    String country,
    String deviceId,
    Instant timestamp,
    int score,
    RiskBand riskBand,
    String classification,
    List<String> contributingRules,
    String model,
    Instant scoredAt) {

  public static ScoredTransaction from(TransactionRequest request, ScoreResult result) {
    return new ScoredTransaction(
        request.id(),
        request.customerId(),
        request.amount(),
        request.currency(),
        request.merchant(),
        request.merchantCategory(),
        request.country(),
        request.deviceId(),
        request.timestamp(),
        result.score(),
        result.riskBand(),
        result.classification(),
        result.contributingRules(),
        result.model(),
        Instant.now());
  }
}
