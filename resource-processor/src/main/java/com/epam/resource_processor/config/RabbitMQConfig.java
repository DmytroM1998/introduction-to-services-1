package com.epam.resource_processor.config;

import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

  @Value("${resource.rabbitmq.queue:resource.uploaded}") 
  private String queueName;
  
  @Bean
  public Queue resourceQueue() {
    return new Queue(queueName, true);
  }
}
