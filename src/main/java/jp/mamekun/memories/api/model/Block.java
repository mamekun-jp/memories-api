package jp.mamekun.memories.api.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
@Table(name = "blocked_user")
public class Block {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID userId;
    private UUID blockedUserId;
    private ZonedDateTime timestamp;

    public Block(UUID userId, UUID blockedUserId, ZonedDateTime timestamp) {
        this.userId = userId;
        this.blockedUserId = blockedUserId;
        this.timestamp = timestamp;
    }
}
