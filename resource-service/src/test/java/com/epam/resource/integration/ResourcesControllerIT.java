package com.epam.resource.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.epam.resource.repository.ResourceRepository;
import com.epam.resource.service.RabbitMQService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3;

/**
 * JUnit Integration Test
 *
 * Covers the full resource lifecycle over the real HTTP layer:
 *   POST /resources -> GET /resources/{id} -> DELETE /resources?id=…
 *
 * Infrastructure is provided by Testcontainers (PostgreSQL, LocalStack S3, RabbitMQ).
 * Eureka service-discovery is disabled so the service starts in isolation.
 * Projected assumptions: Docker can pull these images, mapped ports are accessible,
 * JPA can create the schema, and application startup creates the bucket and exchange.
 * These infrastructure assumptions require verification on a Docker-enabled machine.
 * 
 * This class WON'T run without Docker on. 
 * 
 * For macOS and Linux, the following environment variable should be declared first, 
 * if Colima or Podman are used:
 * TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
 * Source: https://java.testcontainers.org/supported_docker_environment/ 
 * 
 * There are 2 ways to run it:
 * 1. Run the class in IDEA with the mentioned environment variable in its Run Configuration.
 * 2. Run these commands in Terminal from the project root:
 * `export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`
 * `mvn -B -ntp -f resource-service/pom.xml '-Dit.test=ResourcesControllerIT' verify`
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = "resource.rabbitmq.exchange=resource.uploaded")
@Testcontainers(disabledWithoutDocker = true)
@Tag("docker")
class ResourcesControllerIT {

    // ─── Infrastructure ───────────────────────────────────────────────────────

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
                    .withDatabaseName("resource-db")
                    .withUsername("postgres")
                    .withPassword("postgres");

    @Container
    static LocalStackContainer localstack =
            new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.3"))
                    .withServices(S3);

    @Container
    static RabbitMQContainer rabbitmq =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.12-alpine"));

    // ─── Dynamic properties ───────────────────────────────────────────────────

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // DataSource
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        // Flyway / DDL auto-create for tests
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");

        // LocalStack S3
        registry.add("resource.storage.endpoint", () -> localstack.getEndpointOverride(S3).toString());
        registry.add("resource.storage.region", localstack::getRegion);
        registry.add("resource.storage.access-key", localstack::getAccessKey);
        registry.add("resource.storage.secret-key", localstack::getSecretKey);
        registry.add("resource.storage.bucket", () -> "test-resources");

        // RabbitMQ
        registry.add("spring.rabbitmq.host", rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", rabbitmq::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbitmq::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitmq::getAdminPassword);

        // Disable Eureka so the service can start without a registry
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("eureka.client.register-with-eureka", () -> "false");
        registry.add("eureka.client.fetch-registry", () -> "false");

        // Song-service HTTP calls are mocked, while storage and messaging are real.
        registry.add("song.service.ref.name", () -> "localhost:19999");
    }
    // ─── Test ─────────────────────────────────────────────────────────────────

    @MockitoBean
    RestTemplate restTemplateBean;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    ResourceRepository resourceRepository;

    @Autowired
    S3Client s3Client;

    @Autowired
    RabbitTemplate rabbitTemplate;

    @MockitoSpyBean
    RabbitMQService publisher;

    private RabbitAdmin rabbitAdmin;
    private String notificationQueue;

    @BeforeEach
    void setupMock() {
        Mockito.when(restTemplateBean.exchange(
                Mockito.anyString(),
                Mockito.eq(HttpMethod.DELETE),
                Mockito.isNull(),
                Mockito.eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"ids\":[]}"));
        rabbitAdmin = new RabbitAdmin(rabbitTemplate);
        Queue queue = new Queue("upload-test-" + java.util.UUID.randomUUID(), false, false, true);
        notificationQueue = rabbitAdmin.declareQueue(queue);
        rabbitAdmin.declareBinding(new Binding(notificationQueue, Binding.DestinationType.QUEUE,
                "resource.uploaded", "resource.uploaded", null));
    }

    @AfterEach
    void removeNotificationQueue() {
        if (notificationQueue != null) {
            rabbitAdmin.deleteQueue(notificationQueue);
        }
    }

    @Test
    void validMp3Upload_persistsBytesAndNotifiesBeforeLifecycleCleanup() throws IOException {
        // --- UPLOAD ---
        byte[] mp3Bytes;
        try (InputStream sample = getClass().getResourceAsStream("/valid-sample.mp3")) {
            assertThat(sample).isNotNull();
            mp3Bytes = sample.readAllBytes();
        }

        HttpHeaders uploadHeaders = new HttpHeaders();
        uploadHeaders.setContentType(MediaType.parseMediaType("audio/mpeg"));
        HttpEntity<byte[]> uploadRequest = new HttpEntity<>(mp3Bytes, uploadHeaders);

        ResponseEntity<JsonNode> uploadResponse =
                restTemplate.postForEntity("/resources", uploadRequest, JsonNode.class);

        assertThat(uploadResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(uploadResponse.getBody()).isNotNull();
        assertThat(uploadResponse.getBody().path("id").isIntegralNumber()).isTrue();

        long resourceId = uploadResponse.getBody().path("id").longValue();
        assertThat(resourceId).isPositive();
        assertThat(uploadResponse.getBody().size()).isEqualTo(1);

        var stored = resourceRepository.findById(resourceId).orElseThrow();
        URI location = URI.create(stored.getLocation());
        assertThat(location.getScheme()).isEqualTo("s3");
        assertThat(location.getHost()).isEqualTo("test-resources");
        String objectKey = location.getPath().substring(1);
        var storedBytes = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                .bucket(location.getHost()).key(objectKey).build());
        assertThat(storedBytes.asByteArray()).isEqualTo(mp3Bytes);
        assertThat(storedBytes.response().contentType()).isEqualTo("audio/mpeg");

        Message notification = rabbitTemplate.receive(notificationQueue, 5000);
        assertThat(notification).isNotNull();
        assertThat(notification.getBody()).isEqualTo(Long.toString(resourceId).getBytes(StandardCharsets.UTF_8));
        assertThat(notification.getMessageProperties().getContentType()).isEqualTo("application/json");
        assertThat(notification.getMessageProperties().getReceivedExchange()).isEqualTo("resource.uploaded");
        assertThat(notification.getMessageProperties().getReceivedRoutingKey()).isEqualTo("resource.uploaded");

        // --- GET ---
        ResponseEntity<byte[]> getResponse = restTemplate.exchange(
                "/resources/" + resourceId, HttpMethod.GET, null, byte[].class);

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody()).isEqualTo(mp3Bytes);

        // --- DELETE ---
        ResponseEntity<JsonNode> deleteResponse = restTemplate.exchange(
                "/resources?id=" + resourceId, HttpMethod.DELETE, null, JsonNode.class);

        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(deleteResponse.getBody()).isNotNull();
        assertThat(deleteResponse.getBody().path("ids").isArray()).isTrue();

        assertThat(deleteResponse.getBody().path("ids"))
                .anySatisfy(id -> assertThat(id.longValue()).isEqualTo(resourceId));

        // --- VERIFY DELETED ---
        ResponseEntity<byte[]> missingResponse = restTemplate.exchange(
                "/resources/" + resourceId, HttpMethod.GET, null, byte[].class);
        assertThat(missingResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resourceRepository.findById(resourceId)).isEmpty();
        assertThatThrownBy(() -> s3Client.headObject(HeadObjectRequest.builder()
                .bucket(location.getHost()).key(objectKey).build()))
                .isInstanceOfSatisfying(S3Exception.class, failure ->
                        assertThat(failure.statusCode()).isEqualTo(404));
    }

    @Test
    void publicationFailsAfterPersistence_removesDatabaseRecordAndS3Object() {
        long recordsBefore = resourceRepository.count();
        var listRequest = ListObjectsV2Request.builder().bucket("test-resources").build();
        var objectsBefore = s3Client.listObjectsV2(listRequest).contents();
        Mockito.doThrow(new AmqpException("injected publication failure"))
                .when(publisher).sendResourceId(Mockito.anyLong());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("audio/mpeg"));
        byte[] binary = {0x49, 0x44, 0x33, 0, (byte) 0xff};
        var response = restTemplate.postForEntity("/resources",
                new HttpEntity<>(binary, headers), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().path("errorCode").asText()).isEqualTo("500");
        assertThat(response.getBody().has("id")).isFalse();
        Mockito.verify(publisher).sendResourceId(Mockito.anyLong());
        assertThat(resourceRepository.count()).isEqualTo(recordsBefore);
        assertThat(s3Client.listObjectsV2(listRequest).contents()).containsExactlyInAnyOrderElementsOf(objectsBefore);
        assertThat(rabbitTemplate.receive(notificationQueue, 100)).isNull();
    }

}
