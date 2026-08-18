package com.bluecollar.auth.security;

import com.bluecollar.auth.config.JwtProperties;
import com.bluecollar.auth.entity.UserAccount;
import com.bluecollar.auth.exception.InvalidTokenException;
import com.bluecollar.auth.exception.TokenExpiredException;
import com.bluecollar.common.security.AuthenticatedUser;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Service
@SuppressFBWarnings(
        value = {
                "EI_EXPOSE_REP2",
                "CT_CONSTRUCTOR_THROW"
        },
        justification = """
                JwtProperties is a Spring-managed immutable configuration bean.
                The service intentionally fails fast during application startup
                if the JWT secret is invalid or missing.
                """
)
public class JwtService {

    private final JwtProperties jwtProperties;
    private final SecretKey secretKey;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.secretKey = Keys.hmacShaKeyFor(
                Decoders.BASE64.decode(encodeSecret(jwtProperties.getSecret()))
        );
    }

    public String generateAccessToken(UserAccount userAccount) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.getAccessTokenExpirationMs());

        return Jwts.builder()
                .subject(userAccount.getId().toString())
                .claim("email", userAccount.getEmail())
                .claim("role", userAccount.getRole().name())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    public AuthenticatedUser parseAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return new AuthenticatedUser(
                    UUID.fromString(claims.getSubject()),
                    claims.get("email", String.class),
                    com.bluecollar.auth.entity.UserRole.valueOf(
                            claims.get("role", String.class))
            );

        } catch (ExpiredJwtException exception) {
            throw new TokenExpiredException();
        } catch (Exception exception) {
            throw new InvalidTokenException("Invalid access token");
        }
    }

    public long getAccessTokenExpirationMs() {
        return jwtProperties.getAccessTokenExpirationMs();
    }

    public long getRefreshTokenExpirationMs() {
        return jwtProperties.getRefreshTokenExpirationMs();
    }

    public String generatePasswordResetToken(UUID userAccountId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + 3600000);

        return Jwts.builder()
                .subject(userAccountId.toString())
                .claim("type", "password_reset")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    public Claims validatePasswordResetToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            if (!"password_reset".equals(claims.get("type", String.class))) {
                throw new InvalidTokenException("Invalid token type");
            }

            return claims;
        } catch (ExpiredJwtException exception) {
            throw new TokenExpiredException();
        } catch (Exception exception) {
            throw new InvalidTokenException("Invalid password reset token");
        }
    }

    private String encodeSecret(String secret) {

        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT secret must be configured");
        }

        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);

        if (bytes.length >= 32) {
            return Base64.getEncoder().encodeToString(bytes);
        }

        byte[] padded = Arrays.copyOf(bytes, 32);
        return Base64.getEncoder().encodeToString(padded);
    }
}