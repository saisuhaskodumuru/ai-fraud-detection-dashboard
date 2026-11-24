package com.saisuhas.fraud.service;

import java.util.Optional;

import com.saisuhas.fraud.model.ScoreResult;
import com.saisuhas.fraud.model.TransactionRequest;

/**
 * Pluggable model client. Implementations return empty when they cannot
 * score a transaction, so the caller can fall back to the local scorer.
 */
public interface FraudScoringClient {
  Optional<ScoreResult> score(TransactionRequest request);
}
