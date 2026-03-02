package jp.mamekun.memories.api.repository;

import jp.mamekun.memories.api.model.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    long deleteByUserId(UUID userId);

    long deleteByExpiresAtBefore(ZonedDateTime cutoff);
}