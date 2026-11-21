package com.microservice.storage.dto;

import com.microservice.storage.entity.StorageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class StorageDto {

  @Null(message = "Storage id must not be provided")
  private Long id;

  @NotNull(message = "Storage type is required")
  private StorageType storageType;

  @NotBlank(message = "Bucket is required")
  @Size(min = 1, max = 100, message = "Bucket must be between 1 and 100 characters")
  private String bucket;

  @NotBlank(message = "Path is required")
  @Size(min = 1, max = 200, message = "Path must be between 1 and 200 characters")
  private String path;
}
