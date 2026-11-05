package jp.mamekun.memories.api.repository;

import jp.mamekun.memories.api.model.SavedPost;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SavedPostRepository extends JpaRepository<SavedPost, UUID> {
    boolean existsByUserIdAndPostId(UUID userId, UUID postId);

    void deleteAllByUserIdAndPostId(UUID userId, UUID postId);

    void deleteAllByUserId(UUID userId);

    void deleteAllByPostId(UUID postId);
}
