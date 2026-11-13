package com.microservice.storage.health;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.endpoint.EndpointUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component("storageEurekaHealthIndicator")
public class EurekaHealthIndicator implements HealthIndicator {

  private final ObjectProvider<EurekaClient> eurekaClientProvider;
  private final RestTemplate restTemplate;

  public EurekaHealthIndicator(
      ObjectProvider<EurekaClient> eurekaClientProvider,
      RestTemplateBuilder restTemplateBuilder) {
    this.eurekaClientProvider = eurekaClientProvider;
    this.restTemplate = restTemplateBuilder
        .connectTimeout(Duration.ofSeconds(1))
        .readTimeout(Duration.ofSeconds(1))
        .build();
  }

  @Override
  public Health health() {
    EurekaClient eurekaClient = eurekaClientProvider.getIfAvailable();
    if (eurekaClient == null) {
      return Health.down().withDetail("eureka", "Eureka client is unavailable").build();
    }

    InstanceInfo localInstance = eurekaClient.getApplicationInfoManager().getInfo();
    List<String> serviceUrls = EndpointUtils.getServiceUrlsFromConfig(
        eurekaClient.getEurekaClientConfig(), "default", false);
    boolean registered = serviceUrls.stream().anyMatch(serverUrl -> isInstanceUp(serverUrl, localInstance));
    if (registered) {
      return Health.up().withDetail("instanceId", localInstance.getId()).build();
    }
    return Health.down()
        .withDetail("instanceId", localInstance.getId())
        .withDetail("eureka", "Storage Service instance is not registered as UP")
        .build();
  }

  private boolean isInstanceUp(String serverUrl, InstanceInfo localInstance) {
    var instanceUri = UriComponentsBuilder.fromUriString(serverUrl)
        .pathSegment("apps", localInstance.getAppName(), localInstance.getId())
        .build()
        .encode()
        .toUri();
    try {
      Map<?, ?> response = restTemplate.getForObject(instanceUri, Map.class);
      if (response == null) {
        return false;
      }
      Object instance = response.get("instance");
      Map<?, ?> registeredInstance = instance instanceof Map<?, ?> instanceDetails
          ? instanceDetails
          : response;
      return localInstance.getId().equals(registeredInstance.get("instanceId"))
          && InstanceInfo.InstanceStatus.UP.name().equals(registeredInstance.get("status"));
    } catch (RestClientException exception) {
      return false;
    }
  }
}
