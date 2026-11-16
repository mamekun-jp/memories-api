package jp.mamekun.memories.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZonedDateTime;
import java.util.UUID;

/** A single-use invitation to create an account in this space, created by a space admin. */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Invite {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String token;

    private UUID createdBy;
    private ZonedDateTime createdAt;
    private ZonedDateTime expiresAt;
    /** The account created with this invite; set once it has been used. */
    private UUID usedBy;
    private ZonedDateTime usedAt;
    private Boolean revoked;

    public Invite(String token, UUID createdBy, ZonedDateTime createdAt, ZonedDateTime expiresAt) {
        this.token = token;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.revoked = false;
    }

    public String status(ZonedDateTime now) {
        if (usedBy != null) return "USED";
        if (Boolean.TRUE.equals(revoked)) return "REVOKED";
        if (expiresAt != null && now.isAfter(expiresAt)) return "EXPIRED";
        return "ACTIVE";
    }

    public boolean isUsable(ZonedDateTime now) {
        return "ACTIVE".equals(status(now));
    }
}
