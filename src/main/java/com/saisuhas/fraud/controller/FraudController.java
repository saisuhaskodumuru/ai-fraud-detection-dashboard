package com.saisuhas.fraud.controller;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.saisuhas.fraud.model.RiskBand;
import com.saisuhas.fraud.model.ScoredTransaction;
import com.saisuhas.fraud.model.TransactionRequest;
import com.saisuhas.fraud.service.FraudScoringService;
import com.saisuhas.fraud.store.TransactionStore;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
@Validated
public class FraudController {

  private final FraudScoringService scoringService;
  private final TransactionStore store;

  public FraudController(FraudScoringService scoringService, TransactionStore store) {
    this.scoringService = scoringService;
    this.store = store;
  }

  @PostMapping("/transactions/score")
  public ResponseEntity<ScoredTransaction> score(@Valid @RequestBody TransactionRequest request) {
    ScoredTransaction scored = scoringService.scoreAndStore(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(scored);
  }

  @GetMapping("/transactions")
  public List<ScoredTransaction> list(@RequestParam(required = false) String risk) {
    List<ScoredTransaction> all = store.all();
    if (risk == null || risk.isBlank()) {
      return all;
    }
    RiskBand band = RiskBand.valueOf(risk.toUpperCase());
    return all.stream().filter(t -> t.riskBand() == band).toList();
  }

  @GetMapping("/dashboard/stats")
  public Map<String, Object> stats() {
    List<ScoredTransaction> all = store.all();
    long total = all.size();
    long fraud = all.stream()
        .filter(t -> t.riskBand() == RiskBand.HIGH || t.riskBand() == RiskBand.CRITICAL)
        .count();
    double fraudRate = total == 0 ? 0.0 : (double) fraud / total * 100.0;
    double avgScore = all.stream().mapToInt(ScoredTransaction::score).average().orElse(0.0);
    Map<String, Long> byRisk = new LinkedHashMap<>();
    for (RiskBand band : RiskBand.values()) {
      byRisk.put(band.name(),
          all.stream().filter(t -> t.riskBand() == band).count());
    }
    List<Map<String, Object>> topMerchants = all.stream()
        .filter(t -> t.riskBand() == RiskBand.HIGH || t.riskBand() == RiskBand.CRITICAL)
        .collect(Collectors.groupingBy(ScoredTransaction::merchant, Collectors.counting()))
        .entrySet().stream()
        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
        .limit(5)
        .map(e -> Map.<String, Object>of("merchant", e.getKey(), "fraudCount", e.getValue()))
        .toList();

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("totalTransactions", total);
    result.put("fraudCount", fraud);
    result.put("fraudRatePercent", Math.round(fraudRate * 100.0) / 100.0);
    result.put("averageScore", Math.round(avgScore * 100.0) / 100.0);
    result.put("byRiskBand", byRisk);
    result.put("topRiskyMerchants", topMerchants);
    return result;
  }

  @GetMapping("/customers/{id}/classification")
  public Map<String, Object> customerClassification(@PathVariable("id") String customerId) {
    List<ScoredTransaction> mine = store.byCustomer(customerId);
    double avgScore = mine.stream().mapToInt(ScoredTransaction::score).average().orElse(0.0);
    int maxScore = mine.stream().mapToInt(ScoredTransaction::score).max().orElse(0);
    long fraud = mine.stream()
        .filter(t -> t.riskBand() == RiskBand.HIGH || t.riskBand() == RiskBand.CRITICAL)
        .count();
    RiskBand band = RiskBand.fromScore((int) Math.round(avgScore));
    String lastSeen = mine.stream()
        .map(ScoredTransaction::timestamp)
        .max(Comparator.naturalOrder())
        .map(Object::toString)
        .orElse(null);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("customerId", customerId);
    result.put("transactionCount", mine.size());
    result.put("averageScore", Math.round(avgScore * 100.0) / 100.0);
    result.put("maxScore", maxScore);
    result.put("fraudCount", fraud);
    result.put("riskBand", band.name());
    result.put("classification", band.classification());
    result.put("lastTransaction", lastSeen);
    return result;
  }
}
