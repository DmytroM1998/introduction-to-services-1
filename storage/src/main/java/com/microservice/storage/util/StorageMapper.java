package com.microservice.storage.util;

import com.microservice.storage.dto.StorageDto;
import com.microservice.storage.entity.StorageEntity;

public class StorageMapper {
  
  public static StorageDto toDto(StorageEntity storageEntity) {
    return new StorageDto(
        storageEntity.getId(), 
        storageEntity.getStorageType(), 
        storageEntity.getBucket(), 
        storageEntity.getPath());
  }
  
  public static StorageEntity toEntity(StorageDto storageDto) {
    return new StorageEntity(
        storageDto.getId(), 
        storageDto.getStorageType(), 
        storageDto.getBucket(), 
        storageDto.getPath());
  }
}
