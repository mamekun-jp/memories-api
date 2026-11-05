package jp.mamekun.memories.api.repository;

import jp.mamekun.memories.api.model.StoryView;
import jp.mamekun.memories.api.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StoryViewRepository extends JpaRepository<StoryView, UUID> {
    boolean existsByPostIdAndViewer(UUID postId, User viewer);

    List<StoryView> findAllByPostIdOrderByTimestampDesc(UUID postId);

    void deleteAllByViewer(User viewer);

    void deleteAllByPostId(UUID postId);
}
