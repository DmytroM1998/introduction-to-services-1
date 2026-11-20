package com.epam.resource_processor.service;

import com.epam.resource_processor.dto.SongDto;
import com.epam.resource_processor.tracing.TraceContext;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

@Component
@Slf4j
@AllArgsConstructor
public class ResourceMessageListener implements Consumer<Message<?>> {
  
  private final ResourceMetadataService resourceMetadataService;
  private final CommunicationService communicationService;
  private final TraceContext traceContext;

  @Override
  public void accept(Message<?> message) {
    String incomingTraceId = message.getHeaders().get(
        TraceContext.AMQP_HEADER_TRACE_ID, String.class);
    traceContext.initTraceId(incomingTraceId);
    try {
      Long resourceId = parseResourceId(message.getPayload());
      log.info("Received resource ID {} for processing", resourceId);

      byte[] resourceData = communicationService.requestResourceBinary(resourceId);
      SongDto songMetadata = resourceMetadataService.processResourceMetadata(resourceId, resourceData);
      communicationService.saveSongMetadata(resourceId, songMetadata);
      communicationService.notifyResourceProcessed(resourceId);

      log.info("Completed processing for resource ID {}", resourceId);
    } finally {
      traceContext.clear();
    }
  }

  private Long parseResourceId(Object payload) {
    String resourceId;
    if (payload instanceof byte[] bytes) {
      resourceId = new String(bytes, StandardCharsets.UTF_8);
    } else if (payload instanceof String value) {
      resourceId = value;
    } else if (payload instanceof Number value) {
      resourceId = value.toString();
    } else {
      throw new IllegalArgumentException(
          "Unsupported resource upload message payload type: "
              + (payload == null ? "null" : payload.getClass().getName()));
    }
    try {
      return Long.valueOf(resourceId);
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("Invalid resource ID in upload message", exception);
    }
  }
}
