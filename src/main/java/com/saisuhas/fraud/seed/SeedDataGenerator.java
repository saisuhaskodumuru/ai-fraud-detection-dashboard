package com.saisuhas.fraud.seed;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.saisuhas.fraud.model.TransactionRequest;
import com.saisuhas.fraud.service.FraudScoringService;

/**
 * Seeds 200 synthetic transactions on startup with a fixed random seed,
 * so the dashboard is never empty and the demo is reproducible. A few
 * known fraud patterns are planted: high-value foreign transactions,
 * a velocity burst, night-time gambling transactions, and a new-device
 * foreign purchase. No real customer data is used anywhere.
 */
@Component
@Order(1)
public class SeedDataGenerator implements CommandLineRunner {

  private static final Logger log = LoggerFactory.getLogger(SeedDataGenerator.class);

  private static final List<String> MERCHANTS = List.of(
      "Amazon", "Walmart", "Target", "Best Buy", "Delta Air Lines",
      "Uber", "Netflix", "Whole Foods", "Shell", "Starbucks");
  private static final List<String> CATEGORIES = List.of(
      "GROCERIES", "RETAIL", "ELECTRONICS", "TRAVEL", "DINING",
      "ENTERTAINMENT", "FUEL", "GAMBLING", "CRYPTO");

  private final FraudScoringService scoringService;

  public SeedDataGenerator(FraudScoringService scoringService) {
    this.scoringService = scoringService;
  }

  @Override
  public void run(String... args) {
    Random random = new Random(42);
    Instant now = Instant.now();
    for (int i = 0; i < 200; i++) {
      int customer = 1 + random.nextInt(20);
      String customerId = "CUST-%03d".formatted(customer);
      String merchant = MERCHANTS.get(random.nextInt(MERCHANTS.size()));
      String category = CATEGORIES.get(random.nextInt(CATEGORIES.size()));
      String country = random.nextDouble() < 0.85 ? "US" : pick(random, "GB", "CA", "NG", "RU");
      String device = "DEV-" + (1 + random.nextInt(3));
      BigDecimal amount = BigDecimal.valueOf(10 + random.nextDouble() * 900)
          .setScale(2, RoundingMode.HALF_UP);
      Instant timestamp = now.minus(random.nextInt(72), ChronoUnit.HOURS)
          .minus(random.nextInt(60), ChronoUnit.MINUTES);
      scoringService.scoreAndStore(new TransactionRequest(
          "SEED-%03d".formatted(i + 1), customerId, amount, "USD",
          merchant, category, country, device, timestamp));
    }

    // Planted fraud patterns, so the dashboard shows real risk bands.
    Instant base = now.minus(30, ChronoUnit.MINUTES);
    scoringService.scoreAndStore(new TransactionRequest("SEED-FRAUD-1", "CUST-007",
        new BigDecimal("12400.00"), "USD", "Crypto Exchange X", "CRYPTO", "NG",
        "DEV-UNKNOWN-1", base));
    scoringService.scoreAndStore(new TransactionRequest("SEED-FRAUD-2", "CUST-007",
        new BigDecimal("9800.00"), "USD", "Crypto Exchange X", "CRYPTO", "NG",
        "DEV-UNKNOWN-1", base.plus(2, ChronoUnit.MINUTES)));
    scoringService.scoreAndStore(new TransactionRequest("SEED-FRAUD-3", "CUST-012",
        new BigDecimal("7500.00"), "USD", "Night Casino", "GAMBLING", "RU",
        "DEV-UNKNOWN-2", now.minus(2, ChronoUnit.HOURS)));
    for (int i = 0; i < 6; i++) {
      scoringService.scoreAndStore(new TransactionRequest(
          "SEED-VELOCITY-" + (i + 1), "CUST-003",
          new BigDecimal("450.00"), "USD", "Best Buy", "ELECTRONICS", "US",
          "DEV-1", base.plus(i, ChronoUnit.MINUTES)));
    }
    log.info("Seeded 200 synthetic transactions plus planted fraud patterns");
  }

  private static String pick(Random random, String... values) {
    return values[random.nextInt(values.length)];
  }
}
