package com.epam.resource_processor.service;

import com.epam.resource_processor.dto.SongDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class CommunicationService {

  private static final String HTTP_PROTOCOL = "http://";
  private static final String EP_RESOURCES_GET_BY_ID = "/resources/{resourceId}";
  private static final String EP_SONGS = "/songs";
  
  private final RestTemplate restTemplate;
  private final RetryTemplate retryTemplate;
  private final String resourceServiceName;
  private final String songServiceName;

  public CommunicationService(
      RestTemplate restTemplate,
      RetryTemplate retryTemplate,
      @Value("${resource.service.ref.name:resource-service}") String resourceServiceName,
      @Value("${song.service.ref.name:song-service}") String songServiceName) {
    this.restTemplate = restTemplate;
    this.retryTemplate = retryTemplate;
    this.resourceServiceName = resourceServiceName;
    this.songServiceName = songServiceName;
  }

  public void saveSongMetadata(Long resourceId, SongDto songMetadata) {
    String songsPostEndpoint = HTTP_PROTOCOL + songServiceName + EP_SONGS;
    try {
      retryTemplate.execute(context -> restTemplate.postForEntity(
          songsPostEndpoint,
          songMetadata,
          Void.class));
    } catch (HttpClientErrorException exception) {
      if (!exception.getStatusCode().equals(HttpStatus.CONFLICT)) {
        throw exception;
      }
      log.info("Song metadata for resource ID {} already exists; treating delivery as complete", resourceId);
    }
  }

  public byte[] requestResourceBinary(Long resourceId) {
    String resourcesGetEndpoint = HTTP_PROTOCOL + resourceServiceName + EP_RESOURCES_GET_BY_ID;
    ResponseEntity<byte[]> resourceResponse = retryTemplate.execute(context ->
        restTemplate.getForEntity(
            resourcesGetEndpoint,
            byte[].class,
            resourceId));
    byte[] resourceData = resourceResponse.getBody();
    if (resourceData == null) {
      throw new IllegalStateException(
          "Resource Service returned an empty resource for ID " + resourceId);
    }
    return resourceData;
  }
}
