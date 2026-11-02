package com.epam.resource.contract;

import au.com.dius.pact.core.model.messaging.MessageInteraction;
import au.com.dius.pact.provider.MessageAndMetadata;
import au.com.dius.pact.provider.PactVerifyProvider;
import au.com.dius.pact.provider.junit5.MessageTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import au.com.dius.pact.provider.spring.junit5.MockMvcTestTarget;
import com.epam.resource.entity.ResourceEntity;
import com.epam.resource.repository.ResourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Provider("resource-service")
@PactFolder("src/test/resources/pacts")
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
        "eureka.client.enabled=false",
        "resource.storage.bucket=resources",
        "resource.rabbitmq.exchange=resource.uploaded"
})
@AutoConfigureMockMvc
public class ResourceUploadPactProviderTest {

    @Autowired
    MockMvc mockMvc;
    @MockitoBean
    ResourceRepository resourceRepository;
    @MockitoBean
    S3Client s3Client;
    @MockitoBean
    RabbitTemplate rabbitTemplate;
    @MockitoBean
    RestTemplate restTemplate;

    @BeforeEach
    void configureTarget(PactVerificationContext context) {
        if (context.getInteraction() instanceof MessageInteraction) {
            context.setTarget(new MessageTestTarget(List.of("com.epam.resource.contract")));
        } else {
            context.setTarget(new MockMvcTestTarget(mockMvc));
        }
    }

    @State("resource upload dependencies are available")
    public void dependenciesAreAvailable() {
        when(resourceRepository.save(any(ResourceEntity.class))).thenAnswer(call -> {
            ResourceEntity saved = call.getArgument(0);
            saved.setId(42L);
            return saved;
        });
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void verifyInteraction_uploadHttpOrMessageContract_matchesProvider(PactVerificationContext context) {
        context.verifyInteraction();
    }

    @PactVerifyProvider("a resource uploaded notification")
    public MessageAndMetadata uploadedNotification() throws Exception {
        byte[] audio = sampleBytes();
        mockMvc.perform(post("/resources").contentType("audio/mpeg").content(audio))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(42));

        ArgumentCaptor<String> exchange = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> routingKey = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Message> published = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(exchange.capture(), routingKey.capture(), published.capture());
        Message message = published.getValue();
        return new MessageAndMetadata(message.getBody(), Map.of(
                "contentType", message.getMessageProperties().getContentType(),
                "exchange", exchange.getValue(), "routingKey", routingKey.getValue()));
    }

    private byte[] sampleBytes() throws IOException {
        try (InputStream sample = getClass().getResourceAsStream("/valid-sample.mp3")) {
            assertThat(sample).isNotNull();
            return sample.readAllBytes();
        }
    }
}
