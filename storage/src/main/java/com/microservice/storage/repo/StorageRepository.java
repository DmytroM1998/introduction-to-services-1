package com.microservice.storage.repo;

import com.microservice.storage.entity.StorageEntity;
import com.microservice.storage.entity.StorageType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StorageRepository extends JpaRepository<StorageEntity, Long> {
}
