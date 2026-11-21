package com.epam.resource.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;

@Configuration
public class StorageServiceCircuitBreakerConfiguration {

  @Bean
  public CircuitBreaker storageServiceCircuitBreaker(
      @Value("${resilience4j.circuitbreaker.instances.storage-service.sliding-window-size}") int windowSize,
      @Value("${resilience4j.circuitbreaker.instances.storage-service.minimum-number-of-calls}") int minimumCalls,
      @Value("${resilience4j.circuitbreaker.instances.storage-service.failure-rate-threshold}") float failureRate,
      @Value("${resilience4j.circuitbreaker.instances.storage-service.wait-duration-in-open-state}") Duration openStateDuration,
      @Value("${resilience4j.circuitbreaker.instances.storage-service.permitted-number-of-calls-in-half-open-state}") int halfOpenCalls,
      @Value("${storage.service.ref.name}") String storageServiceRefName) {
    CircuitBreakerConfig config = CircuitBreakerConfig.custom()
        .slidingWindowSize(windowSize)
        .minimumNumberOfCalls(minimumCalls)
        .failureRateThreshold(failureRate)
        .waitDurationInOpenState(openStateDuration)
        .permittedNumberOfCallsInHalfOpenState(halfOpenCalls)
        .recordException(exception ->
            exception instanceof ResourceAccessException || 
            exception instanceof HttpServerErrorException)
        .build();
    return CircuitBreaker.of(storageServiceRefName, config);
  }
}
