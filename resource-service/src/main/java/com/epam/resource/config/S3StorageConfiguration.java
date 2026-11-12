package com.epam.resource.config;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

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

}
