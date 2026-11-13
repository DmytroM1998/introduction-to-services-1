package com.microservice.storage.service;

import com.microservice.storage.dto.StorageDto;
import com.microservice.storage.entity.StorageEntity;
import com.microservice.storage.entity.StorageType;
import com.microservice.storage.exception.ErrorCodeException;
import com.microservice.storage.repo.StorageRepository;
import com.microservice.storage.util.StorageMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
@Slf4j
public class StorageService {

  private static final int IDS_STRING_MAX_LENGTH = 200;
  private static final Pattern IDS_PATTERN = Pattern.compile("[0-9]+(?:,[0-9]+)*");

  private StorageRepository storageRepository;

  public Long createStorage(StorageDto storageDto) {
    StorageEntity storage = StorageMapper.toEntity(storageDto);
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
    validateStorageIds(ids);
    List<Long> parsedIds = parseStorageIds(ids);
    List<StorageEntity> storages = storageRepository.findAllById(parsedIds);
    Set<Long> existingIds = storages.stream()
        .map(StorageEntity::getId)
        .collect(Collectors.toSet());
    List<Long> deletedIds = parsedIds.stream()
        .filter(existingIds::contains)
        .toList();
    storageRepository.deleteAll(storages);
    return deletedIds;
  }

  private void validateStorageIds(String ids) {
    if (!IDS_PATTERN.matcher(ids).matches()) {
      throw new ErrorCodeException(HttpStatus.BAD_REQUEST.value(), 
          "CSV string must contain comma-separated positive integer IDs");
    }
    if (ids.length() >= IDS_STRING_MAX_LENGTH) {
      throw new ErrorCodeException(HttpStatus.BAD_REQUEST.value(),
          String.format("CSV string is too long: received %s characters, maximum allowed is %s",
              ids.length(), IDS_STRING_MAX_LENGTH));
    }
  }

  private List<Long> parseStorageIds(String ids) {
    AtomicReference<String> invalidValue = new AtomicReference<>("");
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
}
