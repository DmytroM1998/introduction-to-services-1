package com.microservice.storage.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "storage_metadata")
@Data
@NoArgsConstructor
public class StorageEntity {

  public StorageEntity(Long id, StorageType storageType, String bucket, String path) {
    this.id = id;
    this.storageType = storageType;
    this.bucket = bucket;
    this.path = path;
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Long id;

  @Column(name = "storage_type", nullable = false)
  @Enumerated(EnumType.STRING)
  private StorageType storageType;

  @Column(name = "bucket", nullable = false, length = 100)
  private String bucket;

  @Column(name = "path", nullable = false, length = 200)
  private String path;
}
