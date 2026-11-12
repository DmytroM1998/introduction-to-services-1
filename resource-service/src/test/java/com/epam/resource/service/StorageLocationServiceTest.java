package com.epam.resource.service;

import com.epam.resource.config.StorageServiceCircuitBreakerConfiguration;
import com.epam.resource.dto.StorageDetails;
import com.epam.resource.entity.StorageType;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StorageLocationServiceTest {

  private RestTemplateBuilder restTemplateBuilder;
  private RestTemplate restTemplate;
  private CircuitBreaker circuitBreaker;
  private StorageLocationService service;

  @BeforeEach
  void setUp() {
    restTemplateBuilder = mock(RestTemplateBuilder.class);
    restTemplate = mock(RestTemplate.class);
    when(restTemplateBuilder.connectTimeout(any(Duration.class))).thenReturn(restTemplateBuilder);
    when(restTemplateBuilder.readTimeout(any(Duration.class))).thenReturn(restTemplateBuilder);
    when(restTemplateBuilder.build()).thenReturn(restTemplate);
    circuitBreaker = new StorageServiceCircuitBreakerConfiguration()
        .storageServiceCircuitBreaker(
            2, 
            2, 
            50, 
            Duration.ofSeconds(30), 
            1, 
            "storage-service");
    service = new StorageLocationService(
        restTemplateBuilder,
        circuitBreaker,
        "http://storage-service",
        "staging-bucket",
        "permanent-bucket",
        "staging/",
        "permanent/");
  }

  @Test
  void getStorage_storageServiceUnavailable_returnsConfiguredResponseShape() {
    when(restTemplate.getForObject("http://storage-service/storages", StorageDetails[].class))
        .thenThrow(new ResourceAccessException("connection refused"));

    StorageDetails fallback = service.getStorage(StorageType.STAGING);

    assertThat(fallback.getId()).isEqualTo(1L);
    assertThat(fallback.getStorageType()).isEqualTo(StorageType.STAGING);
    assertThat(fallback.getBucket()).isEqualTo("staging-bucket");
    assertThat(fallback.getPath()).isEqualTo("staging/");
  }

  @Test
  void getStorage_repeatedStorageFailures_opensCircuitAndReturnsStubWithoutCallingService() {
    when(restTemplate.getForObject("http://storage-service/storages", StorageDetails[].class))
        .thenThrow(new ResourceAccessException("connection refused"));

    StorageDetails first = service.getStorage(StorageType.PERMANENT);
    StorageDetails second = service.getStorage(StorageType.PERMANENT);
    StorageDetails whileOpen = service.getStorage(StorageType.PERMANENT);

    assertThat(first.getBucket()).isEqualTo("permanent-bucket");
    assertThat(second.getPath()).isEqualTo("permanent/");
    assertThat(whileOpen.getId()).isEqualTo(2L);
    assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    verify(restTemplate, org.mockito.Mockito.times(2))
        .getForObject("http://storage-service/storages", StorageDetails[].class);
  }

  @Test
  void getStorage_successfulLookupCachesActualDetailsForLaterOutage() {
    StorageDetails actual = storage(StorageType.STAGING, "actual-staging", "actual/path/");
    StorageDetails permanent = storage(StorageType.PERMANENT, "actual-permanent", "actual/permanent/");
    when(restTemplate.getForObject("http://storage-service/storages", StorageDetails[].class))
        .thenReturn(new StorageDetails[] {permanent, actual})
        .thenThrow(new ResourceAccessException("connection refused"));

    assertThat(service.getStorage(StorageType.STAGING).getBucket()).isEqualTo("actual-staging");
    assertThat(service.getStorage(StorageType.STAGING)).isSameAs(actual);
    assertThat(service.getStorage(StorageType.STAGING).getPath()).isEqualTo("actual/path/");
  }

  @Test
  void getStorage_missingOrDuplicateType_throwsIllegalStateException() {
    when(restTemplate.getForObject("http://storage-service/storages", StorageDetails[].class))
        .thenReturn(new StorageDetails[] {storage(StorageType.PERMANENT, "permanent", "path/")})
        .thenReturn(new StorageDetails[] {
            storage(StorageType.STAGING, "staging-one", "one/"),
            storage(StorageType.STAGING, "staging-two", "two/")
        });

    assertThatThrownBy(() -> service.getStorage(StorageType.STAGING))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Expected one storage");
    assertThatThrownBy(() -> service.getStorage(StorageType.STAGING))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Expected one storage");
  }

  private static StorageDetails storage(StorageType type, String bucket, String path) {
    StorageDetails details = new StorageDetails();
    details.setId(42L);
    details.setStorageType(type);
    details.setBucket(bucket);
    details.setPath(path);
    return details;
  }
}
