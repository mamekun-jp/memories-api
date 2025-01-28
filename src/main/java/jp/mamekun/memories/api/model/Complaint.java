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
public class Complaint {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID userId;
    private UUID postId;
    private ZonedDateTime timestamp;

    public Complaint(UUID userId, UUID postId, ZonedDateTime timestamp) {
        this.userId = userId;
        this.postId = postId;
        this.timestamp = timestamp;
    }
}
