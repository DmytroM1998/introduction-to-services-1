package com.epam.resource_processor.contract;

import au.com.dius.pact.consumer.MessagePactBuilder;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.consumer.junit5.ProviderType;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.annotations.Pact;
import au.com.dius.pact.core.model.messaging.MessagePact;
import com.epam.resource_processor.dto.SongDto;
import com.epam.resource_processor.service.CommunicationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.stream.bindings.resourceMessageListener-in-0.destination=resource.uploaded"
})
@Import(TestChannelBinderConfiguration.class)
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "resource-service", providerType = ProviderType.ASYNCH,
        pactVersion = PactSpecVersion.V3)
class ResourceUploadedPactConsumerTest {

    @Autowired
    InputDestination input;

    @MockitoBean
    CommunicationService communicationService;

    @Pact(consumer = "resource-processor-messaging")
    MessagePact uploadedResource(MessagePactBuilder builder) {
        return builder.given("resource upload dependencies are available")
                .expectsToReceive("a resource uploaded notification")
                .withMetadata(Map.of("contentType", "application/json",
                        "exchange", "resource.uploaded", "routingKey", "resource.uploaded"))
                .withContent("42", "application/json")
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "uploadedResource")
    void contractNotificationOnConfiguredBinding_submitsParsedMetadata(MessagePact pact) throws IOException {
        try (InputStream sample = getClass().getResourceAsStream("/valid-sample.mp3")) {
            assertThat(sample).isNotNull();
            when(communicationService.requestResourceBinary(42L)).thenReturn(sample.readAllBytes());
        }

        var message = pact.getMessages().get(0);
        assertThat(message.getMetadata()).containsEntry("routingKey", "resource.uploaded");
        input.send(MessageBuilder.withPayload(message.contentsAsBytes())
                .setHeader("contentType", message.getMetadata().get("contentType")).build(),
                message.getMetadata().get("exchange").toString());

        verify(communicationService).requestResourceBinary(42L);
        ArgumentCaptor<SongDto> song = ArgumentCaptor.forClass(SongDto.class);
        verify(communicationService).saveSongMetadata(eq(42L), song.capture());
        assertThat(song.getValue().getId()).isEqualTo(42L);
        assertThat(song.getValue().getName()).isEqualTo("Test Title");
    }
}
