package jp.mamekun.memories.api.repository;

import jp.mamekun.memories.api.model.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface CommentRepository extends JpaRepository<Comment, UUID> {
    List<Comment> findByPostId(UUID postId);

    Integer countCommentsByPostId(UUID postId);

    @Query("SELECT DISTINCT c.owner.id from Comment c WHERE c.postId = :postId AND c.owner.id != :ownerId")
    List<UUID> findOwnersByPostId(UUID postId, UUID ownerId);
}
