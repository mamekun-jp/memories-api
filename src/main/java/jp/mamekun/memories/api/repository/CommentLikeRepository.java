package jp.mamekun.memories.api.repository;

import jp.mamekun.memories.api.model.CommentLike;
import jp.mamekun.memories.api.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CommentLikeRepository extends JpaRepository<CommentLike, UUID> {
    boolean existsByCommentIdAndUser(UUID commentId, User user);

    void deleteAllByCommentIdAndUser(UUID commentId, User user);

    void deleteAllByCommentId(UUID commentId);

    void deleteAllByUser(User user);

    /** Rows of [commentId, likeCount] for the given comments. */
    @Query("SELECT cl.commentId, COUNT(cl) FROM CommentLike cl WHERE cl.commentId IN :commentIds GROUP BY cl.commentId")
    List<Object[]> countByCommentIds(@Param("commentIds") Collection<UUID> commentIds);

    @Query("SELECT cl.commentId FROM CommentLike cl WHERE cl.commentId IN :commentIds AND cl.user.id = :userId")
    List<UUID> findLikedCommentIds(@Param("commentIds") Collection<UUID> commentIds, @Param("userId") UUID userId);

    @Modifying
    @Query("DELETE FROM CommentLike cl WHERE cl.commentId IN (SELECT c.id FROM Comment c WHERE c.postId = :postId)")
    void deleteAllByPostId(@Param("postId") UUID postId);

    @Modifying
    @Query("DELETE FROM CommentLike cl WHERE cl.commentId IN (SELECT c.id FROM Comment c WHERE c.owner = :owner)")
    void deleteAllOnCommentsBy(@Param("owner") User owner);
}
