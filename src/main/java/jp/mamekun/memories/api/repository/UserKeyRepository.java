package jp.mamekun.memories.api.repository;

import jp.mamekun.memories.api.model.User;
import jp.mamekun.memories.api.model.UserKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserKeyRepository extends JpaRepository<UserKey, UUID> {
    Optional<UserKey> findByUser_Id(UUID userId);

    Optional<UserKey> findByUser(User user);
}
