package com.saisuhas.fraud.model;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TransactionRequest(
    @NotBlank @Size(max = 64) String id,
    @NotBlank @Size(max = 64) String customerId,
    @NotNull @DecimalMin(value = "0.01", inclusive = true) BigDecimal amount,
    @NotBlank @Size(max = 3) String currency,
    @NotBlank @Size(max = 128) String merchant,
    @NotBlank @Size(max = 64) String merchantCategory,
    @NotBlank @Size(max = 2) String country,
    @NotBlank @Size(max = 64) String deviceId,
    @NotNull Instant timestamp) {
}
