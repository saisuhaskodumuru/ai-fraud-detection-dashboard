package com.saisuhas.fraud.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "azure.ml")
public record AzureMlProperties(String endpoint, String apiKey) {
  public boolean isConfigured() {
    return endpoint != null && !endpoint.isBlank();
  }
}
