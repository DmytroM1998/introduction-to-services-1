package com.epam.resource_processor.service;

import com.epam.resource_processor.dto.SongDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;

@Service
@Slf4j
public class CommunicationService {

  private static final String HTTP_PROTOCOL = "http://";
  private static final String EP_RESOURCES_GET_BY_ID = "/resources/{resourceId}";
  private static final String EP_SONGS = "/songs";
  
  private final RestTemplate restTemplate;
  private final RetryTemplate retryTemplate;
  private final RabbitTemplate rabbitTemplate;
  private final String resourceServiceName;
  private final String songServiceName;
  private final String resourceProcessedExchange;
  private final String resourceProcessedRoutingKey;

  public CommunicationService(
      RestTemplate restTemplate,
      RetryTemplate retryTemplate,
      RabbitTemplate rabbitTemplate,
      @Value("${resource.service.ref.name:resource-service}") String resourceServiceName,
      @Value("${song.service.ref.name:song-service}") String songServiceName,
      @Value("${resource.processed.exchange:resource.processed}") String resourceProcessedExchange,
      @Value("${resource.processed.routing-key:resource.processed}") String resourceProcessedRoutingKey) {
    this.restTemplate = restTemplate;
    this.retryTemplate = retryTemplate;
    this.rabbitTemplate = rabbitTemplate;
    this.resourceServiceName = resourceServiceName;
    this.songServiceName = songServiceName;
    this.resourceProcessedExchange = resourceProcessedExchange;
    this.resourceProcessedRoutingKey = resourceProcessedRoutingKey;
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

  public void notifyResourceProcessed(Long resourceId) {
    Message message = MessageBuilder.withBody(Long.toString(resourceId).getBytes(StandardCharsets.UTF_8))
        .setContentType("text/plain")
        .build();
    retryTemplate.execute(context -> {
      rabbitTemplate.send(resourceProcessedExchange, resourceProcessedRoutingKey, message);
      return null;
    });
  }
}
