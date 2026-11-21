package com.epam.resource.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

  @Bean
  public TopicExchange resourceExchange(
      @Value("${resource.rabbitmq.exchange:resource.uploaded}") String exchangeName) {
    return new TopicExchange(exchangeName, true, false);
  }

  @Bean
  public TopicExchange resourceProcessedExchange(
      @Value("${resource.processed.exchange:resource.processed}") String exchangeName) {
    return new TopicExchange(exchangeName, true, false);
  }

  @Bean
  public Queue resourceProcessedQueue(
      @Value("${resource.processed.queue:resource.processed}") String queueName) {
    return new Queue(queueName, true);
  }

  @Bean
  public Binding resourceProcessedBinding(
      @Qualifier("resourceProcessedQueue") Queue resourceProcessedQueue,
      @Qualifier("resourceProcessedExchange") TopicExchange resourceProcessedExchange,
      @Value("${resource.processed.routing-key:resource.processed}") String routingKey) {
    return BindingBuilder.bind(resourceProcessedQueue)
        .to(resourceProcessedExchange)
        .with(routingKey);
  }
}
