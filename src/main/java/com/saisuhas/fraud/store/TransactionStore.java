package com.saisuhas.fraud.store;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Component;

import com.saisuhas.fraud.model.ScoredTransaction;

/**
 * In-memory store. No database, no persistence. All data is synthetic and
 * lives only for the life of the process.
 */
@Component
public class TransactionStore {

  private final List<ScoredTransaction> transactions = new CopyOnWriteArrayList<>();

  public void add(ScoredTransaction transaction) {
    transactions.add(transaction);
  }

  public List<ScoredTransaction> all() {
    return List.copyOf(transactions);
  }

  public List<ScoredTransaction> byCustomer(String customerId) {
    return transactions.stream()
        .filter(t -> t.customerId().equals(customerId))
        .toList();
  }

  public boolean hasCustomerHistory(String customerId) {
    return transactions.stream().anyMatch(t -> t.customerId().equals(customerId));
  }

  public boolean hasCustomerSeenCountry(String customerId, String country) {
    return transactions.stream()
        .anyMatch(t -> t.customerId().equals(customerId)
            && t.country().equalsIgnoreCase(country));
  }

  public boolean hasCustomerSeenDevice(String customerId, String deviceId) {
    return transactions.stream()
        .anyMatch(t -> t.customerId().equals(customerId)
            && t.deviceId().equals(deviceId));
  }

  public long countForCustomerSince(String customerId, Instant since) {
    return transactions.stream()
        .filter(t -> t.customerId().equals(customerId))
        .filter(t -> !t.timestamp().isBefore(since))
        .count();
  }

  public double averageAmountFor(String customerId) {
    return transactions.stream()
        .filter(t -> t.customerId().equals(customerId))
        .mapToDouble(t -> t.amount().doubleValue())
        .average()
        .orElse(0.0);
  }
}
