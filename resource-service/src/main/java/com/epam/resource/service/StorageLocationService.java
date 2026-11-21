package com.epam.resource.service;

import com.epam.resource.dto.StorageDetails;
import com.epam.resource.entity.StorageType;
import com.epam.resource.exception.ErrorCodeException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Duration;

@Service
@Slf4j
public class StorageLocationService {

  private final RestTemplate restTemplate;
  private final CircuitBreaker circuitBreaker;
  private final String storagesUrl;
  private final Map<StorageType, StorageDetails> cache = new ConcurrentHashMap<>();

  public StorageLocationService(
      RestTemplateBuilder restTemplateBuilder,
      CircuitBreaker storageServiceCircuitBreaker,
      @Value("${storage.service.url:http://localhost:8086}") String storageServiceUrl,
      @Value("${storage.fallback.staging.bucket:resources-staging}") String stagingBucket,
      @Value("${storage.fallback.permanent.bucket:resources-permanent}") String permanentBucket,
      @Value("${storage.fallback.staging.path:resources/staging/}") String stagingPath,
      @Value("${storage.fallback.permanent.path:resources/permanent/}") String permanentPath) {
    this.restTemplate = restTemplateBuilder
        .connectTimeout(Duration.ofSeconds(1))
        .readTimeout(Duration.ofSeconds(2))
        .build();
    this.circuitBreaker = storageServiceCircuitBreaker;
    this.storagesUrl = storageServiceUrl + "/storages";
    cache.put(StorageType.STAGING, stubStorage(1L, StorageType.STAGING, stagingBucket, stagingPath));
    cache.put(StorageType.PERMANENT, stubStorage(2L, StorageType.PERMANENT, permanentBucket, permanentPath));
  }

  public StorageDetails getStorage(StorageType type) {
    try {
      //restTemplate operation can be wrapped into a RetryTemplate, but it was removed to speed up the testing
      StorageDetails[] storageList = circuitBreaker.executeSupplier(() ->
          restTemplate.getForObject(storagesUrl, StorageDetails[].class));
      StorageDetails storage = findStorageByType(type, storageList);
      validateStorage(storage, type);
      cache.put(type, storage);
      return storage;
    } catch (ResourceAccessException | HttpServerErrorException | CallNotPermittedException exception) {
      StorageDetails cached = cache.get(type);
      if (cached != null) {
        log.warn("Storage Service is unavailable; using stored {} storage fallback", type, exception);
        return cached;
      }
      log.error("Storage Service is unavailable and no {} storage fallback is configured", type, exception);
      throw new ErrorCodeException(HttpStatus.SERVICE_UNAVAILABLE.value(),
          "Storage Service is unavailable and no cached storage details exist");
    }
  }

  private static StorageDetails findStorageByType(StorageType type, StorageDetails[] storageList) {
    if (storageList == null) {
      throw new IllegalStateException("Storage Service returned no storage list");
    }

    List<StorageDetails> matchingStorages = Arrays.stream(storageList)
        .filter(Objects::nonNull)
        .filter(storage -> storage.getStorageType() == type)
        .toList();
    if (matchingStorages.size() != 1) {
      throw new IllegalStateException(
          String.format("Expected one storage with type '%s' but found %d", 
              type, matchingStorages.size()));
    }
    return matchingStorages.get(0);
  }

  private static void validateStorage(StorageDetails storage, StorageType type) {
    if (storage.getBucket() == null || storage.getPath() == null) {
      throw new IllegalStateException(
          String.format("Storage Service returned invalid details for type '%s'", type));
    }
  }

  private static StorageDetails stubStorage(long id, StorageType type, String bucket, String path) {
    StorageDetails stub = new StorageDetails();
    stub.setId(id);
    stub.setStorageType(type);
    stub.setBucket(bucket);
    stub.setPath(path);
    return stub;
  }
}
