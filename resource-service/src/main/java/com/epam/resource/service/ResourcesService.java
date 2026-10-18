package com.epam.resource.service;

import com.epam.resource.dto.ErrorResponse;
import com.epam.resource.entity.ResourceEntity;
import com.epam.resource.exception.ErrorCodeException;
import com.epam.resource.repository.ResourceRepository;
import com.epam.resource.util.MessageConstants;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResourcesService {

  private static final String HTTP_PROTOCOL_URL = "http://";
  private static final String SONG_SERVICE_SONGS_CONTROLLER = "/songs";
  private static final int IDS_STRING_MAX_LENGTH = 200;
  private static final ErrorCodeException SOMETHING_WENT_WRONG_EXCEPTION = new ErrorCodeException(
    HttpStatus.INTERNAL_SERVER_ERROR.value(), 
    MessageConstants.SOMETHING_WENT_WRONG);
  
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Value("${song.service.ref.name}")
  private String songServiceRefName;

  @Value("${resource.storage.bucket}")
  private String storageBucket;

  private final ResourceRepository resourceRepository;
  private final RestTemplate restTemplate;
  private final S3Client s3Client;

  private String songServiceUrlSongsController;

  @PostConstruct
  private void composeSongServiceReference() {
    songServiceUrlSongsController = HTTP_PROTOCOL_URL + songServiceRefName + SONG_SERVICE_SONGS_CONTROLLER;
    log.info("Song service URL: {}", songServiceUrlSongsController);
  }

  public Map<String, Long> processAndSaveResource(HttpServletRequest request) {
    byte[] mp3file = extractBytesFromRequest(request);
    String objectKey = "resources/" + UUID.randomUUID() + ".mp3";
    String location = "s3://" + storageBucket + "/" + objectKey;
    ResourceEntity resourceEntity = new ResourceEntity(location);

    try {
      saveFileInS3Bucket(objectKey, mp3file);

      ResourceEntity savedEntity = resourceRepository.save(resourceEntity);
      return Map.of("id", savedEntity.getId());
    } catch (RuntimeException saveFailure) {
      try {
        DeleteObjectRequest s3DeletionReq = DeleteObjectRequest.builder()
          .bucket(storageBucket)
          .key(objectKey)
          .build();
        s3Client.deleteObject(s3DeletionReq);
      } catch (RuntimeException cleanupFailure) {
        saveFailure.addSuppressed(cleanupFailure);
        log.error("Failed to remove uploaded object {} after resource persistence failed", location, cleanupFailure);
      }
      throw saveFailure;
    }
  }

  private byte[] extractBytesFromRequest(HttpServletRequest request) {
    try {
      return request.getInputStream().readAllBytes();
    } catch (IOException e) {
      log.error("Failed to process the file", e);
      throw SOMETHING_WENT_WRONG_EXCEPTION;
    }
  }

  private void saveFileInS3Bucket(String objectKey, byte[] mp3file) {
    s3Client.putObject(
        PutObjectRequest.builder()
            .bucket(storageBucket)
            .key(objectKey)
            .contentType("audio/mpeg")
            .build(),
        RequestBody.fromBytes(mp3file));
  }

  public byte[] getResourceById(String id) {
    long parsedId = parseId(id);
    ResourceEntity resource = resourceRepository.findById(parsedId)
        .orElseThrow(() -> new ErrorCodeException(
            HttpStatus.NOT_FOUND.value(),
            String.format("Resource with ID=%d not found", parsedId)));

    ObjectLocation location = parseLocation(resource.getLocation());
    GetObjectRequest s3GetRequest = GetObjectRequest.builder()
      .bucket(location.bucket())
      .key(location.key())
      .build();
    ResponseBytes<GetObjectResponse> binaryResource = s3Client.getObjectAsBytes(s3GetRequest);
    return binaryResource.asByteArray();
  }

  private long parseId(String id) {
    try {
      long parsedId = Long.parseLong(id);
      if (parsedId <= 0) {
        throw new ErrorCodeException(HttpStatus.BAD_REQUEST.value(),
            String.format("Invalid value '%s' for ID. Must be a positive integer", id));
      }
      return parsedId;
    } catch (NumberFormatException e) {
      log.error("Invalid resource ID: {}", id, e);
      throw new ErrorCodeException(HttpStatus.BAD_REQUEST.value(),
          String.format("Invalid value '%s' for ID. Must be a positive integer", id));
    }
  }

  public Map<String, List<Long>> deleteSongsByIds(String ids) {
    validateResourceIdsLength(ids);
    List<Long> idList = parseResourceIds(ids);
    List<Long> deletedIds = deleteExistingResourcesById(idList);
    sendRequestToDeleteSongsByIds(deletedIds);
    return Map.of("ids", deletedIds);
  }

  private void validateResourceIdsLength(String ids) {
    if (ids.length() >= IDS_STRING_MAX_LENGTH) {
      throw new ErrorCodeException(HttpStatus.BAD_REQUEST.value(),
          String.format("CSV string is too long: received %s characters, maximum allowed is %s",
              ids.length(), IDS_STRING_MAX_LENGTH));
    }
  }

  private List<Long> parseResourceIds(String ids) {
    AtomicReference<String> invalidValue = new AtomicReference<>(StringUtils.EMPTY);
    try {
      return Arrays.stream(ids.split(","))
          .map(id -> mapAndReturnInvalidLong(id, invalidValue))
          .toList();
    } catch (NumberFormatException e) {
      log.error("Invalid resource ID: {}", invalidValue.get(), e);
      throw new ErrorCodeException(HttpStatus.BAD_REQUEST.value(),
          String.format("Invalid ID format: '%s'. Only positive integers are allowed", invalidValue.get()));
    }
  }

  private static long mapAndReturnInvalidLong(String id, AtomicReference<String> invalidValue) {
    try {
      long parsedId = Long.parseLong(id);
      if (parsedId <= 0) {
        invalidValue.set(id);
        throw new NumberFormatException("Resource ID must be positive");
      }
      return parsedId;
    } catch (NumberFormatException e) {
      invalidValue.set(id);
      throw e;
    }
  }

  private List<Long> deleteExistingResourcesById(List<Long> idList) {
    List<ResourceEntity> foundResources = resourceRepository.findAllById(idList);
    for (ResourceEntity resource : foundResources) {
      ObjectLocation location = parseLocation(resource.getLocation());
      DeleteObjectRequest s3DeletionReq = DeleteObjectRequest.builder()
        .bucket(location.bucket())
        .key(location.key())
        .build();
      s3Client.deleteObject(s3DeletionReq);
      resourceRepository.delete(resource);
    }
    return foundResources.stream().map(ResourceEntity::getId).toList();
  }

  private ObjectLocation parseLocation(String location) {
    try {
      URI uri = URI.create(location);
      String key = uri.getPath();
      validateRetrievedLocation(uri, key);
      return new ObjectLocation(uri.getHost(), key.substring(1));
    } catch (IllegalArgumentException e) {
      log.error("Invalid S3 location stored for resource: {}", location, e);
      throw SOMETHING_WENT_WRONG_EXCEPTION;
    }
  }

  private static void validateRetrievedLocation(URI uri, String key) {
    if (!"s3".equals(uri.getScheme()) || StringUtils.isBlank(uri.getHost())
        || StringUtils.isBlank(key) || key.equals("/")) {
      throw new IllegalArgumentException("Invalid S3 resource location");
    }
  }

  private void sendRequestToDeleteSongsByIds(List<Long> deletedIds) {
    if (deletedIds.isEmpty()) {
      return;
    }

    String deletedIdsParam = "?id=" + String.join(",", deletedIds.stream().map(String::valueOf).toList());
    try {
      ResponseEntity<String> songDeleteResponse = restTemplate.exchange(
          songServiceUrlSongsController + deletedIdsParam,
          HttpMethod.DELETE,
          null,
          String.class);

      if (songDeleteResponse.getStatusCode() != HttpStatus.OK) {
        ErrorResponse errorResponse = objectMapper.readValue(songDeleteResponse.getBody(), ErrorResponse.class);
        log.error("Error deleting songs: {}", errorResponse.getErrorMessage());
        throw SOMETHING_WENT_WRONG_EXCEPTION;
      }
    } catch (RestClientException e) {
      log.error("Failed to send delete IDs to song-service", e);
      throw SOMETHING_WENT_WRONG_EXCEPTION;
    } catch (JsonProcessingException e) {
      log.error("Failed to parse response from song-service", e);
      throw SOMETHING_WENT_WRONG_EXCEPTION;
    }
  }

  private record ObjectLocation(String bucket, String key) {
  }
}
