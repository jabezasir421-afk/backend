package com.bluecollar.auth.config;

import com.bluecollar.auth.entity.UserAccount;
import com.bluecollar.auth.entity.UserRole;
import com.bluecollar.auth.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrapRunner implements ApplicationRunner {

    private final UserAccountRepository userAccountRepository;
    private final AdminBootstrapProperties adminBootstrapProperties;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        String email = adminBootstrapProperties.getEmail().trim().toLowerCase();
        if (userAccountRepository.existsByEmailIgnoreCase(email)) {
            return;
        }

        userAccountRepository.save(UserAccount.builder()
                .email(email)
                .phoneNumber(adminBootstrapProperties.getPhone())
                .passwordHash(passwordEncoder.encode(adminBootstrapProperties.getPassword()))
                .role(UserRole.ADMIN)
                .emailVerified(true)
                .phoneVerified(true)
                .build());

        log.info("Default admin account created for {}", email);
    }
}
