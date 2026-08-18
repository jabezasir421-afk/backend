package com.bluecollar.auth.repository;

import com.bluecollar.auth.entity.RefreshToken;
import com.bluecollar.auth.entity.UserAccount;
import com.bluecollar.auth.entity.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RefreshTokenRepositoryTest {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    private UserAccount userAccount;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userAccountRepository.deleteAll();

        userAccount = userAccountRepository.saveAndFlush(UserAccount.builder()
                .email("token@example.com")
                .phoneNumber("9876543210")
                .passwordHash("hashed_password")
                .role(UserRole.CUSTOMER)
                .active(true)
                .build());
    }

    @Test
    void findByTokenHashAndRevokedFalseShouldReturnTokenWhenNotRevoked() {
        String tokenHash = "active-token-hash";
        RefreshToken refreshToken = refreshTokenRepository.saveAndFlush(RefreshToken.builder()
                .userAccount(userAccount)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plusSeconds(3600))
                .revoked(false)
                .build());

        Optional<RefreshToken> found = refreshTokenRepository.findByTokenHashAndRevokedFalse(tokenHash);

        assertTrue(found.isPresent());
        assertEquals(refreshToken.getId(), found.get().getId());
        assertEquals(tokenHash, found.get().getTokenHash());
        assertFalse(found.get().getRevoked());
    }

    @Test
    void findByTokenHashAndRevokedFalseShouldReturnEmptyWhenTokenIsRevoked() {
        String tokenHash = "revoked-token-hash";
        refreshTokenRepository.saveAndFlush(RefreshToken.builder()
                .userAccount(userAccount)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plusSeconds(3600))
                .revoked(true)
                .build());

        Optional<RefreshToken> found = refreshTokenRepository.findByTokenHashAndRevokedFalse(tokenHash);

        assertFalse(found.isPresent());
    }

    @Test
    void findByTokenHashAndRevokedFalseShouldReturnEmptyWhenTokenDoesNotExist() {
        Optional<RefreshToken> found = refreshTokenRepository.findByTokenHashAndRevokedFalse("nonexistent-hash");

        assertFalse(found.isPresent());
    }
}
