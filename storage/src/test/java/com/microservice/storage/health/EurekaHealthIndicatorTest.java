package com.microservice.storage.health;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.EurekaClientConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EurekaHealthIndicatorTest {

	@Test
	void healthIsDownWhenStorageInstanceIsNotRegisteredAsUp() throws Exception {
		EurekaClient eurekaClient = mock(EurekaClient.class);
		ApplicationInfoManager applicationInfoManager = mock(ApplicationInfoManager.class);
		EurekaClientConfig eurekaClientConfig = mock(EurekaClientConfig.class);
		InstanceInfo instanceInfo = mock(InstanceInfo.class);
		RestTemplate restTemplate = mock(RestTemplate.class);
		ObjectProvider<EurekaClient> clientProvider = mock(ObjectProvider.class);
		RestTemplateBuilder builder = mock(RestTemplateBuilder.class);

		when(clientProvider.getIfAvailable()).thenReturn(eurekaClient);
		when(eurekaClient.getApplicationInfoManager()).thenReturn(applicationInfoManager);
		when(eurekaClient.getEurekaClientConfig()).thenReturn(eurekaClientConfig);
		when(applicationInfoManager.getInfo()).thenReturn(instanceInfo);
		when(instanceInfo.getId()).thenReturn("storage:8086");
		when(instanceInfo.getAppName()).thenReturn("STORAGE");
		when(eurekaClientConfig.getRegion()).thenReturn("default");
		when(eurekaClientConfig.getAvailabilityZones(anyString())).thenReturn(new String[] {"default"});
		when(eurekaClientConfig.getEurekaServerServiceUrls("default"))
				.thenReturn(List.of("http://eureka:8761/eureka/"));
		when(builder.connectTimeout(any())).thenReturn(builder);
		when(builder.readTimeout(any())).thenReturn(builder);
		when(builder.build()).thenReturn(restTemplate);
		JsonNode downResponse = new ObjectMapper().readTree(
				"{\"instance\":{\"instanceId\":\"storage:8086\",\"status\":\"DOWN\"}}");
		JsonNode upResponse = new ObjectMapper().readTree(
				"{\"instance\":{\"instanceId\":\"storage:8086\",\"status\":\"UP\"}}");
		when(restTemplate.getForObject(any(URI.class), eq(JsonNode.class)))
				.thenReturn(downResponse, upResponse);

		EurekaHealthIndicator indicator = new EurekaHealthIndicator(clientProvider, builder);

		assertThat(indicator.health().getStatus().getCode()).isEqualTo("DOWN");
		assertThat(indicator.health().getStatus().getCode()).isEqualTo("UP");
	}

}
