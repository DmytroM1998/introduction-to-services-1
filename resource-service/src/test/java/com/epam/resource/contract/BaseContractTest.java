package com.epam.resource.contract;

import com.epam.resource.entity.ResourceEntity;
import com.epam.resource.repository.ResourceRepository;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.contract.verifier.messaging.boot.AutoConfigureMessageVerifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.WebApplicationContext;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
        "eureka.client.enabled=false",
        "resource.storage.bucket=resources",
        "resource.rabbitmq.exchange=resource.uploaded"
})
@AutoConfigureMessageVerifier
@Import(BaseContractTest.MessagingContractConfig.class)
public abstract class BaseContractTest {

    @Autowired
    WebApplicationContext webApplicationContext;

    @MockitoBean
    ResourceRepository resourceRepository;

    @MockitoBean
    S3Client s3Client;

    @MockitoBean
    RabbitTemplate rabbitTemplate;

    @MockitoBean
    RestTemplate restTemplate;

    @Autowired
    @Qualifier("resource.uploaded")
    QueueChannel resourceUploadedChannel;

    private byte[] mp3Bytes;

    @BeforeEach
    public void setup() throws IOException {
        RestAssuredMockMvc.webAppContextSetup(webApplicationContext);
        resourceUploadedChannel.clear();
        try (InputStream sample = getClass().getResourceAsStream("/valid-sample.mp3")) {
            assertThat(sample).isNotNull();
            mp3Bytes = sample.readAllBytes();
        }

        ResourceEntity resource = new ResourceEntity("s3://resources/resources/contract-test-resource.mp3");
        resource.setId(1L);
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(resource));
        when(s3Client.getObjectAsBytes(GetObjectRequest.builder()
                .bucket("resources").key("resources/contract-test-resource.mp3").build()))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), mp3Bytes));
        when(resourceRepository.save(any(ResourceEntity.class))).thenAnswer(call -> {
            ResourceEntity saved = call.getArgument(0);
            saved.setId(42L);
            return saved;
        });

        // Bridge the actual AMQP output into the contract verifier, without a broker.
        doAnswer(call -> {
            Message message = call.getArgument(2);
            resourceUploadedChannel.send(MessageBuilder.withPayload(message.getBody())
                    .setHeader("contentType", message.getMessageProperties().getContentType())
                    .build());
            return null;
        }).when(rabbitTemplate).send(eq("resource.uploaded"), eq("resource.uploaded"), any(Message.class));
    }

    public void triggerResourceUpload() {
        RestAssuredMockMvc.given().contentType("audio/mpeg").body(mp3Bytes)
                .post("/resources").then().statusCode(200);
        verify(rabbitTemplate).send(eq("resource.uploaded"), eq("resource.uploaded"), any(Message.class));
    }

    @TestConfiguration
    public static class MessagingContractConfig {
        @Bean("resource.uploaded")
        public QueueChannel resourceUploaded() {
            return new QueueChannel();
        }
    }
}
