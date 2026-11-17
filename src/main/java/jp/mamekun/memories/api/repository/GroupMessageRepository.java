package jp.mamekun.memories.api.repository;

import jp.mamekun.memories.api.model.GroupMessage;
import jp.mamekun.memories.api.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupMessageRepository extends JpaRepository<GroupMessage, UUID> {
    /** The messages a member can see: those sent since they joined, oldest first. */
    List<GroupMessage> findAllByGroupIdAndSentAtGreaterThanEqualOrderBySentAtAsc(UUID groupId, ZonedDateTime since);

    Optional<GroupMessage> findFirstByGroupIdAndSentAtGreaterThanEqualOrderBySentAtDesc(UUID groupId, ZonedDateTime since);

    @Query("select count(m) from GroupMessage m where m.groupId = :groupId and m.event is null " +
            "and m.sender <> :user and m.sentAt > :since")
    long countUnread(UUID groupId, User user, ZonedDateTime since);

    List<GroupMessage> findAllByGroupIdAndSenderAndIdIn(UUID groupId, User sender, List<UUID> ids);

    List<GroupMessage> findAllByGroupId(UUID groupId);

    @Query("select m from GroupMessage m where m.sender = :user or m.targetUser = :user")
    List<GroupMessage> findAllInvolving(User user);

    @Modifying
    @Query("delete from GroupMessage m where m.groupId = :groupId")
    void deleteAllInGroup(UUID groupId);
}
