package com.bluecollar.auth.repository;

import com.bluecollar.auth.entity.UserAccount;
import com.bluecollar.auth.entity.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByEmailIgnoreCase(String email);

    Optional<UserAccount> findByPhoneNumber(String phoneNumber);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByPhoneNumber(String phoneNumber);

    long countByRole(UserRole role);

    @Query("""
            SELECT u FROM UserAccount u
            WHERE (:role IS NULL OR u.role = :role)
              AND (:active IS NULL OR u.active = :active)
              AND (:email IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', :email, '%')))
            """)
    Page<UserAccount> findWithFilters(
            @Param("role") UserRole role,
            @Param("active") Boolean active,
            @Param("email") String email,
            Pageable pageable
    );
}
