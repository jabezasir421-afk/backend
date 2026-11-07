package com.bluecollar.auth.repository;

import com.bluecollar.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    Optional<RefreshToken> findByTokenHashAndRevokedFalse(String tokenHash);

    void deleteByUserAccountId(UUID userAccountId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE RefreshToken r SET r.revoked = true, r.updatedAt = :now WHERE r.userAccount.id = :userAccountId AND r.revoked = false")
    int revokeAllByUserAccountId(@Param("userAccountId") UUID userAccountId, @Param("now") Instant now);
}
