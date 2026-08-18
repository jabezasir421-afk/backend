package com.bluecollar.storage.repository;

import com.bluecollar.storage.entity.FileCategory;
import com.bluecollar.storage.entity.StoredFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StoredFileRepository extends JpaRepository<StoredFile, UUID> {

    Optional<StoredFile> findByIdAndActiveTrue(UUID id);

    List<StoredFile> findByOwnerUserIdAndFileCategoryAndActiveTrue(UUID ownerUserId, FileCategory fileCategory);

    boolean existsByStorageKey(String storageKey);
}
