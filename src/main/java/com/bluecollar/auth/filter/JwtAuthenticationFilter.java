package com.bluecollar.auth.filter;

import com.bluecollar.auth.entity.UserAccount;
import com.bluecollar.auth.entity.UserRole;
import com.bluecollar.auth.repository.UserAccountRepository;
import com.bluecollar.auth.security.JwtService;
import com.bluecollar.common.security.AuthenticatedUser;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserAccountRepository userAccountRepository;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();
        return path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorizationHeader = request.getHeader("Authorization");

        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            String token = authorizationHeader.substring(7);
            try {
                Claims claims = jwtService.parseClaims(token);
                UUID userAccountId = UUID.fromString(claims.getSubject());

                UserAccount userAccount = userAccountRepository.findById(userAccountId).orElse(null);

                if (userAccount != null && Boolean.TRUE.equals(userAccount.getActive())) {
                    boolean sessionValid = true;
                    if (userAccount.getPasswordChangedAt() != null) {
                        Long tokenPwdChangedAt = claims.get("pwd_changed_at", Long.class);
                        long currentPwdChangedAt = userAccount.getPasswordChangedAt().toEpochMilli();
                        if (tokenPwdChangedAt == null || !tokenPwdChangedAt.equals(currentPwdChangedAt)) {
                            sessionValid = false;
                        }
                    }

                    if (sessionValid) {
                        UserRole currentRole = userAccount.getRole();
                        AuthenticatedUser authenticatedUser = new AuthenticatedUser(
                                userAccount.getId(),
                                userAccount.getEmail(),
                                currentRole
                        );

                        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                authenticatedUser,
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + currentRole.name()))
                        );
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    } else {
                        SecurityContextHolder.clearContext();
                    }
                } else {
                    SecurityContextHolder.clearContext();
                }
            } catch (Exception ex) {
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
