package com.epam.resource.contract;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Reference external API consumer, based on the Postman upload expectations.
 * The binary sample checks transport, not MP3 metadata validity.
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "resource-service", pactVersion = PactSpecVersion.V3)
class ResourceUploadPactConsumerTest {

    private static final byte[] AUDIO = {0x49, 0x44, 0x33, 0, (byte) 0xff};

    @Pact(consumer = "resource-upload-client")
    RequestResponsePact upload(PactDslWithProvider builder) {
        return builder.given("resource upload dependencies are available")
                .uponReceiving("a binary MP3 upload")
                .path("/resources").method("POST")
                .withBinaryData(AUDIO, "audio/mpeg")
                .willRespondWith().status(200)
                .headers(Map.of("Content-Type", "application/json"))
                .body(new PactDslJsonBody().integerType("id", 42L))
                .toPact();
    }

    @Pact(consumer = "resource-upload-client")
    RequestResponsePact unsupportedContentType(PactDslWithProvider builder) {
        return builder.uponReceiving("an upload with unsupported JSON content type")
                .path("/resources").method("POST")
                .headers(Map.of("Content-Type", "application/json")).body("{}")
                .willRespondWith().status(400)
                .headers(Map.of("Content-Type", "application/json"))
                .body(new PactDslJsonBody()
                        .stringValue("errorMessage",
                                "Invalid file format: application/json. Only MP3 files are allowed")
                        .stringValue("errorCode", "400"))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "upload")
    void binaryUploadWithSupportedType_returnsPositiveIntegerId(MockServer server) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("audio/mpeg"));
        var response = new RestTemplate().postForEntity(server.getUrl() + "/resources",
                new HttpEntity<>(AUDIO, headers), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().size()).isEqualTo(1);
        assertThat(response.getBody().path("id").isIntegralNumber()).isTrue();
        assertThat(response.getBody().path("id").longValue()).isPositive();
    }

    @Test
    @PactTestFor(pactMethod = "unsupportedContentType")
    void uploadWithJsonContentType_returnsDocumentedBadRequest(MockServer server) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        assertThatThrownBy(() -> new RestTemplate().postForEntity(server.getUrl() + "/resources",
                new HttpEntity<>("{}", headers), JsonNode.class))
                .isInstanceOfSatisfying(HttpClientErrorException.BadRequest.class, failure -> {
                    JsonNode body = failure.getResponseBodyAs(JsonNode.class);
                    assertThat(body).isNotNull();
                    assertThat(body.size()).isEqualTo(2);
                    assertThat(body.path("errorCode").asText()).isEqualTo("400");
                    assertThat(body.path("errorMessage").asText())
                            .isEqualTo("Invalid file format: application/json. Only MP3 files are allowed");
                });
    }
}
