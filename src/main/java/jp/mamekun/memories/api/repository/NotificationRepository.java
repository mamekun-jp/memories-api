package jp.mamekun.memories.api.repository;

import jp.mamekun.memories.api.model.Notification;
import jp.mamekun.memories.api.model.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    // A user never sees notifications about their own actions, nor from people they blocked.
    String FOR_USER = "(n.isPublic = true OR (n.isPublic = false AND n.receiver = :userId))" +
            " AND n.owner.id <> :userId" +
            " AND n.owner.id NOT IN (SELECT b.blockedUserId FROM Block b WHERE b.userId = :userId)" +
            " AND n.timestamp > :timestampAfter";

    @Query("SELECT n FROM Notification n WHERE " + FOR_USER + " ORDER BY n.timestamp DESC")
    List<Notification> findAllForUser(ZonedDateTime timestampAfter, UUID userId, Pageable pageable);

    @Query("SELECT count(n) FROM Notification n WHERE " + FOR_USER)
    Integer countAllByTimestampAndOwner(ZonedDateTime timestampAfter, UUID userId);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.owner = :user OR n.receiver = :userId")
    void deleteAllInvolving(User user, UUID userId);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.targetId = :targetId")
    void deleteAllByTargetId(UUID targetId);
}
