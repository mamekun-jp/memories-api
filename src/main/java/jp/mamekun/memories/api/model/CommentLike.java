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

/** A user liking a comment. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "comment_like", indexes = {
        @Index(name = "idx_comment_like_comment_id", columnList = "comment_id")
})
public class CommentLike {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID commentId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id")
    private User user;

    private ZonedDateTime timestamp;

    public CommentLike(UUID commentId, User user, ZonedDateTime timestamp) {
        this.commentId = commentId;
        this.user = user;
        this.timestamp = timestamp;
    }
}
