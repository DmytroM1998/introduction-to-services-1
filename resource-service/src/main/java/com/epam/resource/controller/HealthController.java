package com.epam.resource.controller;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.EurekaClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.netflix.discovery.endpoint.EndpointUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/health")
public class HealthController {

  private final ObjectProvider<EurekaClient> eurekaClientProvider;
  private final RestTemplate restTemplate;

  public HealthController(
      ObjectProvider<EurekaClient> eurekaClientProvider,
      RestTemplateBuilder restTemplateBuilder) {
    this.eurekaClientProvider = eurekaClientProvider;
    this.restTemplate = restTemplateBuilder
        .setConnectTimeout(Duration.ofSeconds(1))
        .setReadTimeout(Duration.ofSeconds(1))
        .build();
  }

  @GetMapping
  public ResponseEntity<Void> health() {
    return isRegisteredWithEureka()
        ? ResponseEntity.ok().build()
        : ResponseEntity.status(503).build();
  }

  private boolean isRegisteredWithEureka() {
    EurekaClient eurekaClient = eurekaClientProvider.getIfAvailable();
    if (eurekaClient == null) {
      return false;
    }

    InstanceInfo localInstance = eurekaClient.getApplicationInfoManager().getInfo();
    List<String> serviceUrls = EndpointUtils.getServiceUrlsFromConfig(
        eurekaClient.getEurekaClientConfig(),
        "default",
        false);
    return serviceUrls.stream().anyMatch(serverUrl -> isInstanceUp(serverUrl, localInstance));
  }

  private boolean isInstanceUp(String serverUrl, InstanceInfo localInstance) {
    var instanceUri = UriComponentsBuilder.fromUriString(serverUrl)
        .pathSegment("apps", localInstance.getAppName(), localInstance.getId())
        .build()
        .encode()
        .toUri();
    try {
      JsonNode response = restTemplate.getForObject(instanceUri, JsonNode.class);
      JsonNode registeredInstance = getRegisteredInstance(response);
      return registeredInstance != null
          && localInstance.getId().equals(registeredInstance.path("instanceId").asText())
          && InstanceInfo.InstanceStatus.UP.name().equals(registeredInstance.path("status").asText());
    } catch (RestClientException exception) {
      return false;
    }
  }

  private JsonNode getRegisteredInstance(JsonNode response) {
    if (response == null) {
      return null;
    }
    JsonNode instance = response.path("instance");
    return instance.isMissingNode() ? response : instance;
  }
}
