package jp.mamekun.memories.api.repository;

import jp.mamekun.memories.api.model.ChatGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ChatGroupRepository extends JpaRepository<ChatGroup, UUID> {
}
