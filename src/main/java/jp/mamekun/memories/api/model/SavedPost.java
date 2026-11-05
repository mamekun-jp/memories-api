package jp.mamekun.memories.api.model;

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
@Table(name = "saved_post", indexes = {
        @Index(name = "idx_saved_post_user_id", columnList = "user_id")
})
public class SavedPost {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID userId;
    private UUID postId;
    private ZonedDateTime timestamp;

    public SavedPost(UUID userId, UUID postId, ZonedDateTime timestamp) {
        this.userId = userId;
        this.postId = postId;
        this.timestamp = timestamp;
    }
}
