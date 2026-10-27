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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResourcesService {

  private static final String HTTP_PROTOCOL_URL = "http://";
  private static final String SONG_SERVICE_SONGS_CONTROLLER = "/songs";
  private static final int IDS_STRING_MAX_LENGTH = 200;
  public static final ErrorCodeException SOMETHING_WENT_WRONG_EXCEPTION = new ErrorCodeException(
    HttpStatus.INTERNAL_SERVER_ERROR.value(), 
    MessageConstants.SOMETHING_WENT_WRONG);
  
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Value("${song.service.ref.name}")
  private String songServiceRefName;

  private final RabbitMQService rabbitMQService;
  private final ResourceRepository resourceRepository;
  private final RestTemplate restTemplate;
  private final RetryTemplate retryTemplate;
  private final S3BucketService s3BucketService;

  private String songServiceUrlSongsController;

  @PostConstruct
  private void composeSongServiceReference() {
    songServiceUrlSongsController = HTTP_PROTOCOL_URL + songServiceRefName + SONG_SERVICE_SONGS_CONTROLLER;
    log.info("Song service URL: {}", songServiceUrlSongsController);
  }

  public Map<String, Long> processAndSaveResource(HttpServletRequest request) {
    byte[] mp3file = extractBytesFromRequest(request);
    String objectKey = s3BucketService.generateMp3ResourceObjectKey();
    String location = s3BucketService.getResourceStorageBucketLocation(objectKey);
    ResourceEntity resourceEntity = new ResourceEntity(location);
    ResourceEntity savedEntity = null;

    try {
      s3BucketService.saveFileInS3StorageBucket(objectKey, mp3file);
      savedEntity = retryTemplate.execute(context -> resourceRepository.save(resourceEntity));
      sendResourceMessageToProcessor(savedEntity);
      return Map.of("id", savedEntity.getId());
    } catch (RuntimeException saveFailure) {
      tryToDeleteResourceFromDb(savedEntity, saveFailure);
      s3BucketService.deleteObjectOnRollback(objectKey, saveFailure);
      throw saveFailure;
    }
  }

  private void tryToDeleteResourceFromDb(ResourceEntity resourceToDelete, RuntimeException saveFailure) {
    if (resourceToDelete != null) {
      try {
        retryTemplate.execute(context -> {
          resourceRepository.delete(resourceToDelete);
          return null;
        });
      } catch (RuntimeException cleanupFailure) {
        saveFailure.addSuppressed(cleanupFailure);
        log.error("Failed to remove resource record {} after upload failed",
            resourceToDelete.getId(), cleanupFailure);
      }
    }
  }

  private void sendResourceMessageToProcessor(ResourceEntity savedEntity) {
    CustomRetryPattern.execute(() -> {
      rabbitMQService.sendResourceId(savedEntity.getId());
      return null;
    }, ResourcesService::isTransientRabbitFailure);
  }

  private static boolean isTransientRabbitFailure(RuntimeException failure) {
    return failure instanceof org.springframework.amqp.AmqpConnectException
        || failure instanceof org.springframework.amqp.AmqpIOException
        || failure instanceof org.springframework.amqp.AmqpTimeoutException;
  }

  private byte[] extractBytesFromRequest(HttpServletRequest request) {
    try {
      return request.getInputStream().readAllBytes();
    } catch (IOException e) {
      log.error("Failed to process the file", e);
      throw SOMETHING_WENT_WRONG_EXCEPTION;
    }
  }

  public byte[] getResourceById(String id) {
    long parsedId = parseId(id);
    ResourceEntity resource = retryTemplate.execute(context -> resourceRepository.findById(parsedId))
        .orElseThrow(() -> new ErrorCodeException(
            HttpStatus.NOT_FOUND.value(),
            String.format("Resource with ID=%d not found", parsedId)));

    ResponseBytes<GetObjectResponse> binaryResource = s3BucketService.getS3ObjectAsBytes(resource);
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
          .peek(id -> validateIdRange(id, invalidValue))
          .toList();
    } catch (NumberFormatException e) {
      log.error("Invalid resource ID: {}", invalidValue.get(), e);
      throw new ErrorCodeException(HttpStatus.BAD_REQUEST.value(),
          String.format("Invalid ID format: '%s'. Only positive integers are allowed", invalidValue.get()));
    }
  }

  private static long mapAndReturnInvalidLong(String id, AtomicReference<String> invalidValue) {
    try {
      return Long.parseLong(id);
    } catch (NumberFormatException e) {
      invalidValue.set(id);
      throw e;
    }
  }

  private void validateIdRange(Long parsedId, AtomicReference<String> invalidValue) {
    if (parsedId <= 0) {
      invalidValue.set(String.valueOf(parsedId));
      throw new NumberFormatException("Resource ID must be positive");
    }
  }

  private List<Long> deleteExistingResourcesById(List<Long> idList) {
    List<ResourceEntity> foundResources =
        retryTemplate.execute(context -> resourceRepository.findAllById(idList));
    for (ResourceEntity resource : foundResources) {
      s3BucketService.deleteS3Object(resource);
      retryTemplate.execute(context -> {
        resourceRepository.delete(resource);
        return null;
      });
    }
    return foundResources.stream().map(ResourceEntity::getId).toList();
  }

  private void sendRequestToDeleteSongsByIds(List<Long> deletedIds) {
    if (deletedIds.isEmpty()) {
      return;
    }

    String deletedIdsParam = "?id=" + String.join(",", deletedIds.stream().map(String::valueOf).toList());
    try {
      ResponseEntity<String> songDeleteResponse = retryTemplate.execute(context ->
          restTemplate.exchange(
              songServiceUrlSongsController + deletedIdsParam,
              HttpMethod.DELETE,
              null,
              String.class));

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
}
