package com.microservice.storage.service;

import com.microservice.storage.entity.StorageEntity;
import com.microservice.storage.entity.StorageType;
import com.microservice.storage.repo.StorageRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class StorageBootstrap implements ApplicationRunner {

  private final StorageRepository storageRepository;
  private final String stagingBucket;
  private final String permanentBucket;
  private final String stagingPath;
  private final String permanentPath;

  public StorageBootstrap(
      StorageRepository storageRepository,
      @Value("${storage.bootstrap.staging.bucket}") String stagingBucket,
      @Value("${storage.bootstrap.permanent.bucket}") String permanentBucket,
      @Value("${storage.bootstrap.staging.path}") String stagingPath,
      @Value("${storage.bootstrap.permanent.path}") String permanentPath) {
    this.storageRepository = storageRepository;
    this.stagingBucket = stagingBucket;
    this.permanentBucket = permanentBucket;
    this.stagingPath = stagingPath;
    this.permanentPath = permanentPath;
  }

  @Override
  public void run(@NonNull ApplicationArguments args) {
    if (stagingBucket.equals(permanentBucket) || stagingPath.equals(permanentPath)) {
      throw new IllegalStateException("STAGING and PERMANENT must use different buckets and paths");
    }

    List<StorageEntity> storageList = storageRepository.findAll();
    createIfMissing(storageList, StorageType.STAGING, stagingBucket, stagingPath);
    createIfMissing(storageList, StorageType.PERMANENT, permanentBucket, permanentPath);
  }

  private void createIfMissing(List<StorageEntity> availableStorages, StorageType type, String bucket, String path) {
    if (availableStorages.stream().noneMatch(entity -> type == entity.getStorageType())) {
      availableStorages.add(storageRepository.save(new StorageEntity(null, type, bucket, path)));
    }
  }
}
