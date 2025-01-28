package jp.mamekun.memories.api.repository;

import jp.mamekun.memories.api.model.Like;
import jp.mamekun.memories.api.model.User;
import jp.mamekun.memories.api.model.api.UserResponse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LikeRepository extends JpaRepository<Like, UUID> {
    Integer countLikesByPostId(UUID postId);

    Optional<Like> findByPostIdAndUser(UUID postId, User user);

    List<Like> findByPostId(UUID postId);
}
