package jp.mamekun.memories.api.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Follow {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID followerId;
    private UUID followeeId;
    private ZonedDateTime timestamp;

    public Follow(UUID followerId, UUID followeeId, ZonedDateTime timestamp) {
        this.followerId = followerId;
        this.followeeId = followeeId;
        this.timestamp = timestamp;
    }
}

