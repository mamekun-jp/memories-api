package jp.mamekun.memories.api.repository;

import jp.mamekun.memories.api.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
    Optional<User> findByUsername(String username);
    Optional<User> findUserById(UUID id);

    List<User> findAllByIsDeletedOrderByUsername(Boolean isDeleted);
    @Query("SELECT u.deviceToken FROM User u WHERE u.deviceToken IS NOT NULL")
    List<String> findAllDeviceTokenList();

    Optional<User> findUserByDeviceToken(String deviceToken);
}
