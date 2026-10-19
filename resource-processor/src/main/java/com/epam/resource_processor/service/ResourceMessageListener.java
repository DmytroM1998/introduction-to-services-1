package com.epam.resource_processor.service;

import com.epam.resource_processor.dto.SongDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.function.Consumer;

@Component
@Slf4j
public class ResourceMessageListener implements Consumer<Long> {

  private static final String HTTP_PROTOCOL = "http://";
  private static final String EP_RESOURCES_GET_BY_ID = "/resources/{resourceId}";
  private static final String EP_SONGS = "/songs";
  
  private final ResourceMetadataService resourceMetadataService;
  private final RestTemplate restTemplate;
  private final String resourceServiceName;
  private final String songServiceName;

  public ResourceMessageListener(
      ResourceMetadataService resourceMetadataService,
      RestTemplate restTemplate,
      @Value("${resource.service.ref.name:resource-service}") String resourceServiceName,
      @Value("${song.service.ref.name:song-service}") String songServiceName) {
    this.resourceMetadataService = resourceMetadataService;
    this.restTemplate = restTemplate;
    this.resourceServiceName = resourceServiceName;
    this.songServiceName = songServiceName;
  }

  @Override
  public void accept(Long resourceId) {
    log.info("Received resource ID {} for processing", resourceId);

    ResponseEntity<byte[]> resourceResponse = restTemplate.getForEntity(
        HTTP_PROTOCOL + resourceServiceName + EP_RESOURCES_GET_BY_ID,
        byte[].class,
        resourceId);
    byte[] resourceData = resourceResponse.getBody();
    if (resourceData == null) {
      throw new IllegalStateException(
          "Resource Service returned an empty resource for ID " + resourceId);
    }

    SongDto songMetadata =
        resourceMetadataService.processAndPrintResourceMetadata(resourceId, resourceData);
    restTemplate.postForEntity(
        HTTP_PROTOCOL + songServiceName + EP_SONGS,
        songMetadata,
        Void.class);
    log.info("Saved song metadata for resource ID {}", resourceId);
  }
}
