package com.microservice.storage.service;

import com.microservice.storage.entity.StorageEntity;
import com.microservice.storage.entity.StorageType;
import com.microservice.storage.repo.StorageRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StorageBootstrapTest {

  @Test
  void createsBothStorageTypesAndDoesNotDuplicateThemOnRestart() {
    StorageRepository repository = mock(StorageRepository.class);
    List<StorageEntity> records = new ArrayList<>();
    when(repository.findAll()).thenAnswer(invocation -> new ArrayList<>(records));
    when(repository.save(any(StorageEntity.class))).thenAnswer(invocation -> {
      StorageEntity entity = invocation.getArgument(0);
      records.add(entity);
      return entity;
    });

    StorageBootstrap bootstrap = new StorageBootstrap(
        repository, "staging-bucket", "permanent-bucket",
        "resources/staging/", "resources/permanent/");

    bootstrap.run(null);
    bootstrap.run(null);

    assertThat(records).hasSize(2)
        .extracting(StorageEntity::getStorageType)
        .containsExactlyInAnyOrder(StorageType.STAGING, StorageType.PERMANENT);
    assertThat(records).filteredOn(entity -> entity.getStorageType() == StorageType.STAGING)
        .singleElement()
        .satisfies(entity -> {
          assertThat(entity.getBucket()).isEqualTo("staging-bucket");
          assertThat(entity.getPath()).isEqualTo("resources/staging/");
        });
    assertThat(records).filteredOn(entity -> entity.getStorageType() == StorageType.PERMANENT)
        .singleElement()
        .satisfies(entity -> {
          assertThat(entity.getBucket()).isEqualTo("permanent-bucket");
          assertThat(entity.getPath()).isEqualTo("resources/permanent/");
        });
    verify(repository, times(2)).save(any(StorageEntity.class));
  }
}
