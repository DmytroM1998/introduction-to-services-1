package com.microservice.storage.health;

import com.fasterxml.jackson.databind.JsonNode;
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
      JsonNode response = restTemplate.getForObject(instanceUri, JsonNode.class);
      JsonNode registeredInstance = response == null ? null : response.path("instance");
      if (registeredInstance == null || registeredInstance.isMissingNode()) {
        registeredInstance = response;
      }
      return registeredInstance != null
          && localInstance.getId().equals(registeredInstance.path("instanceId").asText())
          && InstanceInfo.InstanceStatus.UP.name().equals(registeredInstance.path("status").asText());
    } catch (RestClientException exception) {
      return false;
    }
  }
}
