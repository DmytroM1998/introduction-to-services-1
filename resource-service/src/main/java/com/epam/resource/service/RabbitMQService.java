package com.epam.resource.service;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RabbitMQService {

  private final RabbitTemplate rabbitTemplate;

  @Value("${resource.rabbitmq.queue:resource.uploaded}")
  private String resourceQueue;

  public RabbitMQService(RabbitTemplate rabbitTemplate) {
    this.rabbitTemplate = rabbitTemplate;
  }

  public void sendResourceId(long resourceId) {
    rabbitTemplate.convertAndSend(resourceQueue, resourceId);
  }
}
