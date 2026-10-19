package com.epam.resource.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

  @Bean
  public TopicExchange resourceExchange(
      @Value("${resource.rabbitmq.exchange:resource.uploaded}") String exchangeName) {
    return new TopicExchange(exchangeName, true, false);
  }
}
