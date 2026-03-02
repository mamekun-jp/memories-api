package jp.mamekun.memories.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "password_reset_token",
        indexes = {
                @Index(name = "idx_password_reset_token_user_id", columnList = "user_id"),
                @Index(name = "idx_password_reset_token_expires_at", columnList = "expires_at"),
                @Index(name = "idx_password_reset_token_token_hash", columnList = "token_hash", unique = true)
        }
)
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * Store only a hash of the token (never store raw tokens).
     */
    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private ZonedDateTime expiresAt;

    @Column(name = "used_at")
    private ZonedDateTime usedAt;

    public boolean isExpired(ZonedDateTime now) {
        return expiresAt != null && now.isAfter(expiresAt);
    }

    public boolean isUsed() {
        return usedAt != null;
    }
}