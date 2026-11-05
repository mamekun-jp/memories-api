package jp.mamekun.memories.api.model;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Records that a user has seen a story, so the story owner can see who watched it.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "story_view", indexes = {
        @Index(name = "idx_story_view_post_id", columnList = "post_id")
})
public class StoryView {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID postId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "viewer_id")
    private User viewer;

    private ZonedDateTime timestamp;

    public StoryView(UUID postId, User viewer, ZonedDateTime timestamp) {
        this.postId = postId;
        this.viewer = viewer;
        this.timestamp = timestamp;
    }
}
