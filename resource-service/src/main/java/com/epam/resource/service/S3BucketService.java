package com.epam.resource.service;

import com.epam.resource.entity.ResourceEntity;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.URI;
import java.util.UUID;

import static com.epam.resource.service.ResourcesService.SOMETHING_WENT_WRONG_EXCEPTION;

@Service
@Slf4j
public class S3BucketService {

  @Value("${resource.storage.bucket}")
  private String storageBucket;

  private final S3Client s3Client;

  public S3BucketService(S3Client s3Client) {
    this.s3Client = s3Client;
  }
  
  public void saveFileInS3StorageBucket(String objectKey, byte[] mp3file) {
    PutObjectRequest request = PutObjectRequest.builder()
        .bucket(storageBucket)
        .key(objectKey)
        .contentType("audio/mpeg")
        .build();
    s3Client.putObject(request, RequestBody.fromBytes(mp3file));
  }

  public void deleteObjectOnRollback(String objectKey, RuntimeException saveFailure) {
    String location = "s3://" + storageBucket + "/" + objectKey;
    try {
      DeleteObjectRequest s3DeletionReq = DeleteObjectRequest.builder()
          .bucket(storageBucket)
          .key(objectKey)
          .build();
      s3Client.deleteObject(s3DeletionReq);
    } catch (RuntimeException cleanupFailure) {
      saveFailure.addSuppressed(cleanupFailure);
      log.error("Failed to remove uploaded object {} after resource persistence failed",
          location, cleanupFailure);
    }
  }

  public ResponseBytes<GetObjectResponse> getS3ObjectAsBytes(ResourceEntity resource) {
    S3ObjectLocation location = parseS3ObjectLocation(resource.getLocation());
    GetObjectRequest s3GetRequest = GetObjectRequest.builder()
      .bucket(location.bucket())
      .key(location.key())
      .build();
    return s3Client.getObjectAsBytes(s3GetRequest);
  }

  public void deleteS3Object(ResourceEntity resource) {
    S3ObjectLocation location = parseS3ObjectLocation(resource.getLocation());
    DeleteObjectRequest s3DeletionReq = DeleteObjectRequest.builder()
        .bucket(location.bucket())
        .key(location.key())
        .build();
    s3Client.deleteObject(s3DeletionReq);
  }

  private S3ObjectLocation parseS3ObjectLocation(String location) {
    try {
      URI uri = URI.create(location);
      String key = uri.getPath();
      validateRetrievedLocation(uri, key);
      return new S3ObjectLocation(uri.getHost(), key.substring(1));
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

  public String generateMp3ResourceObjectKey() {
    return "resources/" + UUID.randomUUID() + ".mp3";
  }

  public String getResourceStorageBucketLocation(String objectKey) {
    return "s3://" + storageBucket + "/" + objectKey;
  }

  private record S3ObjectLocation(String bucket, String key) {
  }
}
