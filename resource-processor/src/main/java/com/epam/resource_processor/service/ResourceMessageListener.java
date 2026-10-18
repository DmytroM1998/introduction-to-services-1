package com.epam.resource_processor.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ResourceMessageListener {

  @RabbitListener(queues = "${resource.rabbitmq.queue:resource.uploaded}")
  public void receiveResourceId(Long resourceId) {
    log.info("Received resource ID {} for processing", resourceId);
  }
}
