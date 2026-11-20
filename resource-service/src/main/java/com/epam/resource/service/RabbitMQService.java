package com.epam.resource.service;

import com.epam.resource.tracing.TraceContext;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
public class RabbitMQService {

  private final RabbitTemplate rabbitTemplate;
  private final TraceContext traceContext;
  private final String resourceExchange;

  public RabbitMQService(
      RabbitTemplate rabbitTemplate,
      TraceContext traceContext,
      @Value("${resource.rabbitmq.exchange:resource.uploaded}") String resourceExchange) {
    this.rabbitTemplate = rabbitTemplate;
    this.traceContext = traceContext;
    this.resourceExchange = resourceExchange;
  }

  public void sendResourceId(long resourceId) {
    String currentTraceId = traceContext.getTraceId();
    if (currentTraceId == null || currentTraceId.isBlank()) {
      currentTraceId = UUID.randomUUID().toString(); //shouldn't be necessary because TraceIdFilter does so
    }
    
    Message message = MessageBuilder.withBody(
            Long.toString(resourceId).getBytes(StandardCharsets.UTF_8))
        .setContentType("application/json")
        .setHeader(TraceContext.AMQP_HEADER_TRACE_ID, currentTraceId)
        .build();
    rabbitTemplate.send(resourceExchange, "resource.uploaded", message);
  }
}
