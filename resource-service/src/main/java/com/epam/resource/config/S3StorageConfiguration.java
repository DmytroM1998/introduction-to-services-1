package com.epam.resource.config;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Configuration
public class S3StorageConfiguration {

  @Bean
  public S3Client buildS3Client(
      @Value("${resource.storage.endpoint}") URI endpoint,
      @Value("${resource.storage.region}") String region,
      @Value("${resource.storage.access-key}") String accessKey,
      @Value("${resource.storage.secret-key}") String secretKey) {
    return S3Client.builder()
        .endpointOverride(endpoint)
        .region(Region.of(region))
        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
        .forcePathStyle(true)
        .build();
  }

  @Bean
  public ApplicationRunner initializeStorageBucket(S3Client s3Client,
                                                   @Value("${resource.storage.bucket}") String bucket) {
    return args -> {
      try {
        s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
      } catch (S3Exception e) {
        if (e.statusCode() != HttpStatus.NOT_FOUND.value()) {
          throw e;
        }
        s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
      }
    };
  }
}
