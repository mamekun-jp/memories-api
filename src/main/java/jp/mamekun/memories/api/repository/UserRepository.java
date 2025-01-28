package jp.mamekun.memories.api.repository;

import jp.mamekun.memories.api.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
    Optional<User> findByUsername(String username);

    List<User> findAllByIsDeletedOrderByUsername(Boolean isDeleted);
}
