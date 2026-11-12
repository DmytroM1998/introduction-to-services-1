package com.epam.resource.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
@Slf4j
public class ResourceProcessedListener {

  private final ResourcesService resourcesService;

  @RabbitListener(queues = "${resource.processed.queue:resource.processed}")
  public void onResourceProcessed(Message message) {
    String body = new String(message.getBody(), StandardCharsets.UTF_8);
    long resourceId = validateResourceId(body);

    log.info("Received processing completion for resource {}", resourceId);
    resourcesService.promoteResourceToPermanent(resourceId);
  }

  private static long validateResourceId(String body) {
    long resourceId;
    try {
      resourceId = Long.parseLong(body);
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("Invalid processed-resource notification: " + body, exception);
    }
    if (resourceId <= 0) {
      throw new IllegalArgumentException("Processed-resource ID must be positive");
    }
    return resourceId;
  }
}
