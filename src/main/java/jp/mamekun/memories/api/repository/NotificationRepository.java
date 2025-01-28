package jp.mamekun.memories.api.repository;

import jp.mamekun.memories.api.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    @Query("SELECT n FROM Notification n WHERE (n.isPublic = true OR (n.isPublic = false AND n.receiver = :userId))" +
            " AND n.timestamp > :timestampAfter ORDER BY n.timestamp DESC")
    List<Notification> findAllForUser(ZonedDateTime timestampAfter, UUID userId);

    @Query("SELECT count(*) FROM Notification n WHERE (n.isPublic = true " +
            " OR (n.isPublic = false AND n.receiver = :userId))" +
            " AND n.timestamp > :timestampAfter")
    Integer countAllByTimestampAndOwner(ZonedDateTime timestampAfter, UUID userId);
}
