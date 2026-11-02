package com.epam.resource.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpTimeoutException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RabbitMQServiceTest {

    @Mock
    RabbitTemplate rabbit;

    @Test
    void sendResourceId_customExchange_sendsUtf8JsonWithConsumerRoutingKey() {
        RabbitMQService service = new RabbitMQService(rabbit, "custom.resource.exchange");

        service.sendResourceId(42L);

        ArgumentCaptor<Message> sent = ArgumentCaptor.forClass(Message.class);
        verify(rabbit).send(eq("custom.resource.exchange"), eq("resource.uploaded"), sent.capture());
        assertThat(sent.getValue().getBody()).isEqualTo("42".getBytes(StandardCharsets.UTF_8));
        assertThat(sent.getValue().getMessageProperties().getContentType()).isEqualTo("application/json");
    }

    @Test
    void sendResourceId_brokerFailure_propagatesFailure() {
        RabbitMQService service = new RabbitMQService(rabbit, "resource.uploaded");
        var failure = new AmqpTimeoutException("publish failed");
        doThrow(failure).when(rabbit).send(eq("resource.uploaded"), eq("resource.uploaded"), any(Message.class));

        assertThatThrownBy(() -> service.sendResourceId(42L)).isSameAs(failure);
    }
}
