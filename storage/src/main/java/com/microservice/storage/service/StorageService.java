package com.microservice.storage.service;

import com.microservice.storage.dto.StorageDto;
import com.microservice.storage.entity.StorageEntity;
import com.microservice.storage.exception.ErrorCodeException;
import com.microservice.storage.repo.StorageRepository;
import com.microservice.storage.util.StorageMapper;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class StorageService {

  private static final int IDS_STRING_MAX_LENGTH = 200;
  private static final Pattern IDS_PATTERN = Pattern.compile("[0-9]+(?:,[0-9]+)*");

  private StorageRepository storageRepository;

  public Long createStorage(StorageDto storageDto) {
    var storage = StorageMapper.toEntity(storageDto);
    storage.setId(null);
    return storageRepository.save(storage).getId();
  }

  public List<StorageDto> getAllStorages() {
    return storageRepository.findAll().stream()
        .map(StorageMapper::toDto)
        .toList();
  }

  @Transactional
  public List<Long> deleteStoragesByIds(String ids) {
    validateStorageIdsLength(ids);
    if (ids == null || !IDS_PATTERN.matcher(ids).matches()) {
      throw new ErrorCodeException(HttpStatus.BAD_REQUEST.value(), "CSV string must contain comma-separated positive integer IDs");
    }

    Set<Long> parsedIds = new LinkedHashSet<>();
    try {
      for (String id : ids.split(",")) {
        long parsedId = Long.parseLong(id);
        if (parsedId <= 0) {
          throw new ErrorCodeException(HttpStatus.BAD_REQUEST.value(), "Storage IDs must be positive integers");
        }
        parsedIds.add(parsedId);
      }
    } catch (NumberFormatException e) {
      throw new ErrorCodeException(HttpStatus.BAD_REQUEST.value(), "CSV string contains an invalid storage ID");
    }

    var storages = storageRepository.findAllById(parsedIds);
    Set<Long> existingIds = storages.stream()
        .map(StorageEntity::getId)
        .collect(Collectors.toSet());
    List<Long> deletedIds = parsedIds.stream()
        .filter(existingIds::contains)
        .toList();
    storageRepository.deleteAll(storages);
    return deletedIds;
  }

  private void validateStorageIdsLength(String ids) {
    if (ids == null || ids.length() >= IDS_STRING_MAX_LENGTH) {
      int length = ids == null ? 0 : ids.length();
      throw new ErrorCodeException(HttpStatus.BAD_REQUEST.value(),
          String.format("CSV string is invalid or too long: received %s characters, maximum allowed is less than %s", length, IDS_STRING_MAX_LENGTH));
    }
  }
}
