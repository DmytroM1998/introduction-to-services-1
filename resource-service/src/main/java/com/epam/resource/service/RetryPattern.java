package com.epam.resource.service;

import java.util.function.Predicate;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class RetryPattern {

  private static final int MAX_ATTEMPTS = 3;
  private static final long INITIAL_DELAY_MILLIS = 200;

  private RetryPattern() {
  }

  public static <T> T execute(Supplier<T> operation, Predicate<RuntimeException> retryable) {
    for (int attempt = 1; ; attempt++) {
      try {
        return operation.get();
      } catch (RuntimeException failure) {
        if (attempt == MAX_ATTEMPTS || !retryable.test(failure)) {
          throw failure;
        }

        log.warn("Transient operation failure; retrying attempt {}/{}", attempt + 1, MAX_ATTEMPTS, failure);
        try {
          Thread.sleep(INITIAL_DELAY_MILLIS * attempt);
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException("Interrupted while waiting to retry operation", interrupted);
        }
      }
    }
  }
}
