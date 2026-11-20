package com.epam.resource_processor.service;

import com.epam.resource_processor.tracing.TraceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.MediaType;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CommunicationServiceTracePropagationTests {

  private static final String TRACE_ID = "trace-from-message";

  @AfterEach
  void clearTraceContext() {
    TraceContext.clear();
  }

  @Test
  void requestResourceBinary_whenTraceContextExists_sendsTraceIdHeader() {
    RestTemplate restTemplate = new RestTemplate();
    RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    CommunicationService communicationService = new CommunicationService(
        restTemplate,
        new RetryTemplate(),
        rabbitTemplate,
        "resource-service",
        "song-service",
        "resource.processed",
        "resource.processed");
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server.expect(requestTo("http://resource-service/resources/42"))
        .andExpect(header(TraceContext.HTTP_HEADER_TRACE_ID, TRACE_ID))
        .andRespond(withSuccess().contentType(MediaType.APPLICATION_OCTET_STREAM).body(new byte[] {1, 2, 3}));
    TraceContext.initTraceId(TRACE_ID);

    byte[] response = communicationService.requestResourceBinary(42L);

    assertThat(response).containsExactly(1, 2, 3);
    server.verify();
  }

  @Test
  void notifyResourceProcessed_whenTraceContextExists_addsTraceIdToMessage() {
    RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    CommunicationService communicationService = new CommunicationService(
        new RestTemplate(),
        new RetryTemplate(),
        rabbitTemplate,
        "resource-service",
        "song-service",
        "resource.processed",
        "resource.processed");
    TraceContext.initTraceId(TRACE_ID);

    communicationService.notifyResourceProcessed(42L);

    ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
    verify(rabbitTemplate).send(
        org.mockito.ArgumentMatchers.eq("resource.processed"),
        org.mockito.ArgumentMatchers.eq("resource.processed"),
        messageCaptor.capture());
    Object traceHeader = messageCaptor.getValue().getMessageProperties()
        .getHeader(TraceContext.AMQP_HEADER_TRACE_ID);
    assertThat(traceHeader).isEqualTo(TRACE_ID);
  }
}
