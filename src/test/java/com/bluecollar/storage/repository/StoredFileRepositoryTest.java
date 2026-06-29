package com.bluecollar.storage.repository;

import com.bluecollar.storage.entity.EntityType;
import com.bluecollar.storage.entity.FileCategory;
import com.bluecollar.storage.entity.StoredFile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StoredFileRepositoryTest {

    @Autowired
    private StoredFileRepository storedFileRepository;

    @Test
    void findByIdAndActiveTrueShouldReturnFileWhenActive() {
        StoredFile storedFile = storedFileRepository.saveAndFlush(StoredFile.builder()
                .ownerUserId(UUID.randomUUID())
                .entityType(EntityType.WORKER)
                .fileCategory(FileCategory.PROFILE_PHOTO)
                .storageKey("owner/profile/uuid/photo.jpg")
                .originalName("photo.jpg")
                .mimeType("image/jpeg")
                .sizeBytes(1024L)
                .active(true)
                .build());

        Optional<StoredFile> found = storedFileRepository.findByIdAndActiveTrue(storedFile.getId());

        assertTrue(found.isPresent());
        assertEquals(storedFile.getId(), found.get().getId());
        assertEquals("photo.jpg", found.get().getOriginalName());
    }

    @Test
    void findByIdAndActiveTrueShouldReturnEmptyWhenFileIsInactive() {
        StoredFile storedFile = storedFileRepository.saveAndFlush(StoredFile.builder()
                .ownerUserId(UUID.randomUUID())
                .entityType(EntityType.WORKER)
                .fileCategory(FileCategory.PROFILE_PHOTO)
                .storageKey("owner/profile/uuid/deleted.jpg")
                .originalName("deleted.jpg")
                .mimeType("image/jpeg")
                .sizeBytes(512L)
                .active(false)
                .build());

        Optional<StoredFile> found = storedFileRepository.findByIdAndActiveTrue(storedFile.getId());

        assertTrue(found.isEmpty());
    }
}
