package com.epam.resource.service;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class RabbitMQService {

  private final RabbitTemplate rabbitTemplate;
  private final String resourceExchange;

  public RabbitMQService(
      RabbitTemplate rabbitTemplate,
      @Value("${resource.rabbitmq.exchange:resource.uploaded}") String resourceExchange) {
    this.rabbitTemplate = rabbitTemplate;
    this.resourceExchange = resourceExchange;
  }

  public void sendResourceId(long resourceId) {
    Message message = MessageBuilder.withBody(
            Long.toString(resourceId).getBytes(StandardCharsets.UTF_8))
        .setContentType("application/json")
        .build();
    rabbitTemplate.send(resourceExchange, "resource.uploaded", message);
  }
}
