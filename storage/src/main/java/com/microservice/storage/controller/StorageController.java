package com.microservice.storage.controller;

import com.microservice.storage.dto.StorageDto;
import com.microservice.storage.service.StorageService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@AllArgsConstructor
@RequestMapping("/storages")
public class StorageController {

  private StorageService storageService;

  @PostMapping
  public ResponseEntity<Map<String, Long>> createStorage(@Valid @RequestBody StorageDto storageDto) {
    return ResponseEntity.ok(Map.of("id", storageService.createStorage(storageDto)));
  }

  @GetMapping()
  public ResponseEntity<List<StorageDto>> getAllStorages() {
    return ResponseEntity.ok(storageService.getAllStorages());
  }

  @DeleteMapping()
  public ResponseEntity<List<Long>> deleteStorage(@RequestParam String id) {
    return ResponseEntity.ok(storageService.deleteStoragesByIds(id));
  }
}
