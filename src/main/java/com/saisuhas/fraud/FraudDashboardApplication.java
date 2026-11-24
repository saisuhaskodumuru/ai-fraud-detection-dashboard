package com.saisuhas.fraud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.saisuhas.fraud.config.AzureMlProperties;

@SpringBootApplication
@EnableConfigurationProperties(AzureMlProperties.class)
public class FraudDashboardApplication {
  public static void main(String[] args) {
    SpringApplication.run(FraudDashboardApplication.class, args);
  }
}
