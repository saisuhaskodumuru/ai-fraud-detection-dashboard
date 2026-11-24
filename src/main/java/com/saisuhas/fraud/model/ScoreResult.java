package com.saisuhas.fraud.model;

import java.util.List;

public record ScoreResult(int score, RiskBand riskBand, String classification,
    List<String> contributingRules, String model) {
}
