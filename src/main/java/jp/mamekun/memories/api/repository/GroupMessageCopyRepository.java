package jp.mamekun.memories.api.repository;

import jp.mamekun.memories.api.model.GroupMessageCopy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface GroupMessageCopyRepository extends JpaRepository<GroupMessageCopy, UUID> {
    List<GroupMessageCopy> findAllByRecipientIdAndMessageIdIn(UUID recipientId, Collection<UUID> messageIds);

    @Modifying
    @Query("delete from GroupMessageCopy c where c.messageId in :messageIds")
    void deleteAllForMessages(Collection<UUID> messageIds);

    @Modifying
    @Query("delete from GroupMessageCopy c where c.recipientId = :recipientId")
    void deleteAllForRecipient(UUID recipientId);
}
