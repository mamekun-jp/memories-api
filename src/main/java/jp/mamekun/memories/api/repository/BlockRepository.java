package jp.mamekun.memories.api.repository;

import jp.mamekun.memories.api.model.Block;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BlockRepository extends JpaRepository<Block, UUID> {
    List<Block> findAllByUserId(UUID userId);

    boolean existsByUserIdAndBlockedUserId(UUID userId, UUID blockedUserId);
}
