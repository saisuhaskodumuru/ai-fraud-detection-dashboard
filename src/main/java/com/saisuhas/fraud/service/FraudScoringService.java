package com.saisuhas.fraud.service;

import org.springframework.stereotype.Service;

import com.saisuhas.fraud.model.ScoreResult;
import com.saisuhas.fraud.model.ScoredTransaction;
import com.saisuhas.fraud.model.TransactionRequest;
import com.saisuhas.fraud.store.TransactionStore;

/**
 * Scores a transaction, trying Azure ML first when configured and falling
 * back to the local rule scorer. The scored transaction is stored.
 */
@Service
public class FraudScoringService {

  private final AzureMlFraudScoringClient azureMlClient;
  private final RuleBasedFraudScorer localScorer;
  private final TransactionStore store;

  public FraudScoringService(AzureMlFraudScoringClient azureMlClient,
      RuleBasedFraudScorer localScorer, TransactionStore store) {
    this.azureMlClient = azureMlClient;
    this.localScorer = localScorer;
    this.store = store;
  }

  public ScoredTransaction scoreAndStore(TransactionRequest request) {
    ScoreResult result = azureMlClient.score(request)
        .orElseGet(() -> localScorer.score(request, store));
    ScoredTransaction scored = ScoredTransaction.from(request, result);
    store.add(scored);
    return scored;
  }
}
