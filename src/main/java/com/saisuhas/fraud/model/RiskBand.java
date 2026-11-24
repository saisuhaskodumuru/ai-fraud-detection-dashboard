package com.saisuhas.fraud.model;

public enum RiskBand {
  LOW,
  MEDIUM,
  HIGH,
  CRITICAL;

  public static RiskBand fromScore(int score) {
    if (score >= 75) {
      return CRITICAL;
    }
    if (score >= 55) {
      return HIGH;
    }
    if (score >= 30) {
      return MEDIUM;
    }
    return LOW;
  }

  public String classification() {
    return switch (this) {
      case LOW -> "LEGITIMATE";
      case MEDIUM -> "REVIEW";
      case HIGH -> "FRAUD_SUSPECT";
      case CRITICAL -> "FRAUD_SUSPECT";
    };
  }
}
