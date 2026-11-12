package com.epam.resource.service;

import com.epam.resource.entity.ResourceEntity;
import com.epam.resource.dto.StorageDetails;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static com.epam.resource.service.ResourcesService.SOMETHING_WENT_WRONG_EXCEPTION;

@Service
@Slf4j
public class S3BucketService {

  private final S3Client s3Client;

  public S3BucketService(S3Client s3Client) {
    this.s3Client = s3Client;
  }
  
  public void saveFileInS3StorageBucket(String bucket, String objectKey, byte[] mp3file) {
    PutObjectRequest request = PutObjectRequest.builder()
        .bucket(bucket)
        .key(objectKey)
        .contentType("audio/mpeg")
        .build();
    s3Client.putObject(request, RequestBody.fromBytes(mp3file));
  }

  public void deleteObjectOnRollback(String bucket, String objectKey, RuntimeException saveFailure) {
    String location = "s3://" + bucket + "/" + objectKey;
    try {
      DeleteObjectRequest s3DeletionReq = DeleteObjectRequest.builder()
          .bucket(bucket)
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

  public String copyObjectToStorage(String location, StorageDetails source, StorageDetails destination) {
    S3ObjectLocation sourceLocation = parseS3ObjectLocation(location);
    String sourcePrefix = normalizePrefix(source.getPath());
    if (!source.getBucket().equals(sourceLocation.bucket())
        || !sourceLocation.key().startsWith(sourcePrefix)) {
      throw new IllegalArgumentException("Resource location does not match its stored storage details");
    }

    String destinationLocation = getEquivalentLocation(location, source, destination);
    S3ObjectLocation destinationObject = parseS3ObjectLocation(destinationLocation);
//    String copySource = URLEncoder.encode(
//            sourceLocation.bucket() + "/" + sourceLocation.key(), StandardCharsets.UTF_8)
//        .replace("+", "%20")
//        .replace("%2F", "/");
    s3Client.copyObject(CopyObjectRequest.builder()
        .sourceBucket(sourceLocation.bucket())
        .sourceKey(sourceLocation.key())
        .destinationBucket(destinationObject.bucket())
        .destinationKey(destinationObject.key())
        .build());
    return destinationLocation;
  }

  private String getEquivalentLocation(String location, StorageDetails source, StorageDetails destination) {
    S3ObjectLocation sourceLocation = parseS3ObjectLocation(location);
    String sourcePrefix = normalizePrefix(source.getPath());
    if (!source.getBucket().equals(sourceLocation.bucket())
        || !sourceLocation.key().startsWith(sourcePrefix)) {
      throw new IllegalArgumentException("Resource location does not match its stored storage details");
    }

    String relativeKey = sourceLocation.key().substring(sourcePrefix.length());
    String destinationKey = normalizePrefix(destination.getPath()) + relativeKey;
    return getResourceStorageBucketLocation(destination, destinationKey);
  }

  public boolean isInStorage(String location, StorageDetails storage) {
    S3ObjectLocation objectLocation = parseS3ObjectLocation(location);
    return storage.getBucket().equals(objectLocation.bucket())
        && objectLocation.key().startsWith(normalizePrefix(storage.getPath()));
  }

  public void deleteObject(String location) {
    S3ObjectLocation source = parseS3ObjectLocation(location);
    s3Client.deleteObject(DeleteObjectRequest.builder()
        .bucket(source.bucket())
        .key(source.key())
        .build());
  }

  public String generateMp3ResourceObjectKey(String path) {
    return normalizePrefix(path) + UUID.randomUUID() + ".mp3";
  }

  public String getResourceStorageBucketLocation(StorageDetails storage, String objectKey) {
    return "s3://" + storage.getBucket() + "/" + objectKey;
  }

  private static String normalizePrefix(String path) {
    String prefix = path.trim();
    while (prefix.startsWith("/")) {
      prefix = prefix.substring(1);
    }
    return prefix.isEmpty() || prefix.endsWith("/") ? prefix : prefix + "/";
  }

  private record S3ObjectLocation(String bucket, String key) {
  }
}
