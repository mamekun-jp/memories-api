package jp.mamekun.memories.api.repository;

import jp.mamekun.memories.api.model.Follow;
import jp.mamekun.memories.api.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface FollowRepository extends JpaRepository<Follow, UUID> {
    @Query("SELECT u " +
            "FROM Follow f JOIN User u ON f.followeeId = u.id " +
            "WHERE f.followerId = :followerId")
    List<User> findAllByFollowerId(UUID followerId);

    @Query("SELECT u " +
            "FROM Follow f JOIN User u ON f.followerId = u.id " +
            "WHERE f.followeeId = :followeeId")
    List<User> findFollowersByFolloweeId(UUID followeeId);

    boolean existsByFollowerIdAndFolloweeId(UUID followerId, UUID followeeId);

    void deleteAllByFollowerIdAndFolloweeId(UUID followerId, UUID followeeId);

    Integer countByFolloweeId(UUID followeeId);

    Integer countByFollowerId(UUID followerId);
}
