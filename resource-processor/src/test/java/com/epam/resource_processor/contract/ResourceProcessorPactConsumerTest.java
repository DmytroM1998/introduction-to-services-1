package com.epam.resource_processor.contract;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import com.epam.resource_processor.service.CommunicationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pact Consumer Test — resource-processor → resource-service (HTTP)
 *
 * Verifies that resource-processor's HTTP client interaction with resource-service
 * is correctly defined from the consumer's perspective.
 *
 * Communication style: Synchronous HTTP
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "resource-service", pactVersion = PactSpecVersion.V3)
class ResourceProcessorPactConsumerTest {

    private static final long RESOURCE_ID = 1L;

    @Pact(consumer = "resource-processor", provider = "resource-service")
    RequestResponsePact fetchResourceById(PactDslWithProvider builder) throws IOException {
        return builder
                .given("resource with id 1 exists")
                .uponReceiving("a request to fetch resource 1")
                    .path("/resources/" + RESOURCE_ID)
                    .method("GET")
                .willRespondWith()
                    .status(200)
                    .headers(Map.of("Content-Type", "audio/mpeg"))
                    .withBinaryData(sampleBytes(), "audio/mpeg")
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "fetchResourceById")
    void requestResourceBinary_existingResource_returnsExactMp3Bytes(MockServer mockServer) throws IOException {
        RestTemplate restTemplate = new RestTemplate();
        RetryTemplate retryTemplate = new RetryTemplate();
        String mockHost = mockServer.getUrl().replace("http://", "").replace("https://", "");
        CommunicationService communicationService = new CommunicationService(
                restTemplate, retryTemplate, mockHost, "dummy-song-service");

        byte[] result = communicationService.requestResourceBinary(RESOURCE_ID);

        assertThat(result).isEqualTo(sampleBytes());
    }

    private static byte[] sampleBytes() throws IOException {
        try (InputStream sample = ResourceProcessorPactConsumerTest.class
                .getResourceAsStream("/valid-sample.mp3")) {
            assertThat(sample).isNotNull();
            return sample.readAllBytes();
        }
    }
}
