package com.epam.resource_processor.service;

import com.epam.resource_processor.dto.SongDto;
import com.epam.resource_processor.tracing.TraceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class CommunicationService {

  private static final String HTTP_PROTOCOL = "http://";
  private static final String EP_RESOURCES_GET_BY_ID = "/resources/{resourceId}";
  private static final String EP_SONGS = "/songs";
  
  private final RestTemplate restTemplate;
  private final RetryTemplate retryTemplate;
  private final RabbitTemplate rabbitTemplate;
  private final TraceContext traceContext;
  private final String resourceServiceName;
  private final String songServiceName;
  private final String resourceProcessedExchange;
  private final String resourceProcessedRoutingKey;

  public CommunicationService(
      RestTemplate restTemplate,
      RetryTemplate retryTemplate,
      RabbitTemplate rabbitTemplate,
      TraceContext traceContext,
      @Value("${resource.service.ref.name:resource-service}") String resourceServiceName,
      @Value("${song.service.ref.name:song-service}") String songServiceName,
      @Value("${resource.processed.exchange:resource.processed}") String resourceProcessedExchange,
      @Value("${resource.processed.routing-key:resource.processed}") String resourceProcessedRoutingKey) {
    this.restTemplate = restTemplate;
    this.traceContext = traceContext;
    setupClientInterceptors();
    this.retryTemplate = retryTemplate;
    this.rabbitTemplate = rabbitTemplate;
    this.resourceServiceName = resourceServiceName;
    this.songServiceName = songServiceName;
    this.resourceProcessedExchange = resourceProcessedExchange;
    this.resourceProcessedRoutingKey = resourceProcessedRoutingKey;
  }

  private void setupClientInterceptors() {
    ClientHttpRequestInterceptor interceptor = (HttpRequest request, byte[] body,
        ClientHttpRequestExecution execution) -> {
      String traceId = traceContext.getTraceId();
      if (traceId != null && !traceId.isBlank()) {
        request.getHeaders().set(TraceContext.HTTP_HEADER_TRACE_ID, traceId);
      }
      return execution.execute(request, body);
    };
    List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>(restTemplate.getInterceptors());
    interceptors.add(interceptor);
    restTemplate.setInterceptors(interceptors);
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
        .setHeader(TraceContext.AMQP_HEADER_TRACE_ID, traceContext.getTraceId())
        .build();
    retryTemplate.execute(context -> {
      rabbitTemplate.send(resourceProcessedExchange, resourceProcessedRoutingKey, message);
      return null;
    });
  }
}
