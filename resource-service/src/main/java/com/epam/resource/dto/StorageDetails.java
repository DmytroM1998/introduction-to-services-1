package com.epam.resource.dto;

import com.epam.resource.entity.StorageType;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class StorageDetails {

  private Long id;
  private StorageType storageType;
  private String bucket;
  private String path;
}
