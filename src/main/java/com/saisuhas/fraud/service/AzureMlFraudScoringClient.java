package com.saisuhas.fraud.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.saisuhas.fraud.config.AzureMlProperties;
import com.saisuhas.fraud.model.RiskBand;
import com.saisuhas.fraud.model.ScoreResult;
import com.saisuhas.fraud.model.TransactionRequest;

/**
 * Azure ML scoring client. In the Verizon work this called a real Azure ML
 * endpoint for fraud scores. Here it is a real client with a real HTTP call,
 * configured only by environment variables. When no endpoint is configured
 * it does nothing, and the service falls back to the local rule scorer.
 *
 * Configure with:
 *   AZURE_ML_ENDPOINT=https://<workspace>.azureml.net/.../score
 *   AZURE_ML_API_KEY=<key>
 * The endpoint is expected to accept the transaction JSON and return a JSON
 * object with a numeric "score" field (0-100). Any failure returns empty
 * so the caller falls back to local scoring. The API key is sent only as
 * an Authorization header to the configured endpoint and is never logged.
 */
@Component
public class AzureMlFraudScoringClient implements FraudScoringClient {

  private static final Logger log = LoggerFactory.getLogger(AzureMlFraudScoringClient.class);

  private final AzureMlProperties properties;
  private final RestClient restClient;

  @Autowired
  public AzureMlFraudScoringClient(AzureMlProperties properties) {
    this.properties = properties;
    this.restClient = RestClient.create();
  }

  // Test seam: inject a RestClient.
  AzureMlFraudScoringClient(AzureMlProperties properties, RestClient restClient) {
    this.properties = properties;
    this.restClient = restClient;
  }

  @Override
  public Optional<ScoreResult> score(TransactionRequest request) {
    if (!properties.isConfigured()) {
      return Optional.empty();
    }
    try {
      Map<String, Object> body = Map.of(
          "customerId", request.customerId(),
          "amount", request.amount(),
          "currency", request.currency(),
          "merchant", request.merchant(),
          "merchantCategory", request.merchantCategory(),
          "country", request.country(),
          "deviceId", request.deviceId(),
          "timestamp", request.timestamp().toString());
      Map<?, ?> response = restClient.post()
          .uri(properties.endpoint())
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
          .contentType(MediaType.APPLICATION_JSON)
          .body(body)
          .retrieve()
          .body(Map.class);
      if (response == null || response.get("score") == null) {
        log.warn("Azure ML returned no score, falling back to local scoring");
        return Optional.empty();
      }
      int score = Math.min(100, Math.max(0, ((Number) response.get("score")).intValue()));
      RiskBand band = RiskBand.fromScore(score);
      return Optional.of(new ScoreResult(score, band, band.classification(),
          List.of("AZURE_ML_MODEL_SCORE"), "azure-ml"));
    } catch (Exception e) {
      log.warn("Azure ML scoring failed, falling back to local scoring: {}", e.getMessage());
      return Optional.empty();
    }
  }
}
