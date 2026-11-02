package com.epam.resource.component;

import com.epam.resource.entity.ResourceEntity;
import com.epam.resource.repository.ResourceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.RestTemplate;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

public class ResourceUploadSteps {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    ResourceRepository resourceRepository;
    @Autowired
    S3Client s3Client;
    @Autowired
    RabbitTemplate rabbitTemplate;
    @Autowired
    RestTemplate restTemplate;

    private final Map<Long, ResourceEntity> resources = new HashMap<>();
    private final Map<String, byte[]> objects = new HashMap<>();
    private byte[] fileBytes;
    private MvcResult lastResponse;
    private Long recordedId;

    @Before
    public void resetState() {
        reset(resourceRepository, s3Client, rabbitTemplate, restTemplate);
        resources.clear();
        objects.clear();
        fileBytes = null;
        lastResponse = null;
        recordedId = null;
        AtomicLong nextId = new AtomicLong();

        when(resourceRepository.save(any(ResourceEntity.class))).thenAnswer(call -> {
            ResourceEntity entity = call.getArgument(0);
            entity.setId(nextId.incrementAndGet());
            resources.put(entity.getId(), entity);
            return entity;
        });
        when(resourceRepository.findById(any(Long.class))).thenAnswer(call ->
                Optional.ofNullable(resources.get(call.<Long>getArgument(0))));
        when(resourceRepository.findAllById(any())).thenAnswer(call -> {
            Iterable<Long> ids = call.getArgument(0);
            List<ResourceEntity> found = new ArrayList<>();
            ids.forEach(id -> {
                if (resources.containsKey(id)) {
                    found.add(resources.get(id));
                }
            });
            return found;
        });
        doAnswer(call -> {
            resources.remove(call.<ResourceEntity>getArgument(0).getId());
            return null;
        }).when(resourceRepository).delete(any(ResourceEntity.class));
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenAnswer(call -> {
            PutObjectRequest request = call.getArgument(0);
            RequestBody body = call.getArgument(1);
            try (InputStream stream = body.contentStreamProvider().newStream()) {
                objects.put(request.key(), stream.readAllBytes());
            }
            return PutObjectResponse.builder().build();
        });
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenAnswer(call -> {
            GetObjectRequest request = call.getArgument(0);
            assertThat(objects).containsKey(request.key());
            return ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), objects.get(request.key()));
        });
        when(s3Client.deleteObject(any(DeleteObjectRequest.class))).thenAnswer(call -> {
            objects.remove(call.<DeleteObjectRequest>getArgument(0).key());
            return DeleteObjectResponse.builder().build();
        });
        when(restTemplate.exchange(anyString(), eq(HttpMethod.DELETE), isNull(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"ids\":[]}"));
    }

    @Given("the resource service is running")
    public void theResourceServiceIsRunning() {
        assertThat(mockMvc).isNotNull();
    }

    @Given("a valid MP3 audio file")
    public void aValidMp3AudioFile() throws IOException {
        try (InputStream sample = getClass().getResourceAsStream("/valid-sample.mp3")) {
            assertThat(sample).isNotNull();
            fileBytes = sample.readAllBytes();
        }
    }

    @Given("a plain text file masquerading as audio")
    public void aPlainTextFile() {
        fileBytes = "This is not an MP3 file".getBytes(StandardCharsets.UTF_8);
    }

    @Given("the database rejects the resource record")
    public void theDatabaseRejectsTheResourceRecord() {
        when(resourceRepository.save(any(ResourceEntity.class)))
                .thenThrow(new DataIntegrityViolationException("resource record rejected"));
    }

    @Given("the message publisher rejects the upload notification")
    public void theMessagePublisherRejectsTheUploadNotification() {
        doThrow(new AmqpException("publish rejected")).when(rabbitTemplate)
                .send(anyString(), anyString(), any(Message.class));
    }

    @Given("no resource exists with id {int}")
    public void noResourceExistsWithId(int id) {
        assertThat(resources).doesNotContainKey((long) id);
    }

    @Given("a valid MP3 audio file has been uploaded and the id was recorded")
    public void aValidMp3FileHasBeenUploadedAndIdRecorded() throws Exception {
        aValidMp3AudioFile();
        uploadMp3("audio/mpeg");
        responseStatusIsOk(200);
        responseBodyContainsPositiveId();
    }

    @When("the client uploads the MP3 file with content type {string}")
    @When("the client uploads the file with content type {string}")
    public void uploadMp3(String mediaType) throws Exception {
        lastResponse = mockMvc.perform(post("/resources").contentType(mediaType).content(fileBytes)).andReturn();
    }

    @When("the client requests the resource using the returned id")
    public void getResourceById() throws Exception {
        getResourceByLiteralId(Math.toIntExact(recordedId));
    }

    @When("the client requests the resource with id {int}")
    public void getResourceByLiteralId(int id) throws Exception {
        lastResponse = mockMvc.perform(get("/resources/{id}", id)).andReturn();
    }

    @When("the client deletes the recorded resource")
    public void deleteRecordedResource() throws Exception {
        lastResponse = mockMvc.perform(delete("/resources").param("id", recordedId.toString())).andReturn();
    }

    @When("the client requests the deleted resource")
    public void getDeletedResource() throws Exception {
        getResourceById();
    }

    @Then("the response status is {int} OK")
    @Then("the response status is {int} Bad Request")
    @Then("the response status is {int} Not Found")
    @Then("the response status is {int} Internal Server Error")
    public void responseStatusIsOk(int status) {
        assertThat(lastResponse.getResponse().getStatus()).isEqualTo(status);
    }

    @Then("the response body contains a positive numeric resource id")
    public void responseBodyContainsPositiveId() throws IOException {
        JsonNode body = responseBody();
        assertThat(body.size()).isEqualTo(1);
        assertThat(lastResponse.getResponse().getContentType()).isEqualTo("application/json");
        JsonNode id = body.path("id");
        assertThat(id.isIntegralNumber()).isTrue();
        recordedId = id.longValue();
        assertThat(recordedId).isPositive();
        verify(rabbitTemplate).send(eq("resource.uploaded"), eq("resource.uploaded"),
                argThat((Message message) ->
                        new String(message.getBody(), StandardCharsets.UTF_8).equals(recordedId.toString())
                                && "application/json".equals(message.getMessageProperties().getContentType())));
    }

    @Then("the response body contains the same bytes as the uploaded MP3 file")
    public void responseBodyEqualsUploadedBytes() {
        assertThat(lastResponse.getResponse().getContentAsByteArray()).isEqualTo(fileBytes);
        assertThat(lastResponse.getResponse().getContentType()).isEqualTo("audio/mpeg");
    }

    @Then("the response body contains an errorMessage explaining the invalid file format")
    public void responseBodyContainsInvalidFormatMessage() throws IOException {
        assertThat(responseBody().path("errorMessage").asText()).containsIgnoringCase("invalid file format");
        assertThat(responseBody().path("errorCode").asText()).isEqualTo("400");
    }

    @Then("the invalid format response matches the Postman specification for {string}")
    public void invalidFormatResponseMatchesPostman(String contentType) throws IOException {
        JsonNode body = responseBody();
        assertThat(lastResponse.getResponse().getContentType()).isEqualTo("application/json");
        assertThat(body.size()).isEqualTo(2);
        assertThat(body.path("errorMessage").asText())
                .isEqualTo("Invalid file format: " + contentType + ". Only MP3 files are allowed");
        assertThat(body.path("errorCode").asText()).isEqualTo("400");
        verifyNoInteractions(resourceRepository, s3Client, rabbitTemplate, restTemplate);
    }

    @Then("the upload returns the generic server error without a resource id")
    public void uploadReturnsTheGenericServerError() throws IOException {
        JsonNode body = responseBody();
        assertThat(lastResponse.getResponse().getContentType()).isEqualTo("application/json");
        assertThat(body.size()).isEqualTo(2);
        assertThat(body.path("errorMessage").asText()).isEqualTo("Something went wrong");
        assertThat(body.path("errorCode").asText()).isEqualTo("500");
        assertThat(body.has("id")).isFalse();
    }

    @Then("no uploaded object or resource record remains")
    public void noUploadedObjectOrResourceRecordRemains() {
        assertThat(resources).isEmpty();
        assertThat(objects).isEmpty();
        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
        verifyNoInteractions(restTemplate);
    }

    @Then("no resource notification is published")
    public void noResourceNotificationIsPublished() {
        verifyNoInteractions(rabbitTemplate);
    }

    @Then("the response body contains an errorMessage explaining the resource was not found")
    public void responseBodyContainsNotFoundMessage() throws IOException {
        assertThat(responseBody().path("errorMessage").asText()).containsIgnoringCase("not found");
        assertThat(responseBody().path("errorCode").asText()).isEqualTo("404");
    }

    @And("the response body contains an ids list including the deleted id")
    public void responseBodyContainsIdsListWithDeletedId() throws IOException {
        JsonNode ids = responseBody().path("ids");
        assertThat(ids.isArray()).isTrue();
        assertThat(ids).anySatisfy(id -> assertThat(id.longValue()).isEqualTo(recordedId));
        assertThat(resources).doesNotContainKey(recordedId);
        assertThat(objects).isEmpty();
        verify(restTemplate).exchange(eq("http://song-service/songs?id=" + recordedId),
                eq(HttpMethod.DELETE), isNull(), eq(String.class));
    }

    private JsonNode responseBody() throws IOException {
        return objectMapper.readTree(lastResponse.getResponse().getContentAsByteArray());
    }
}
