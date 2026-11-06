package jp.mamekun.memories.api.repository;

import jp.mamekun.memories.api.model.Message;
import jp.mamekun.memories.api.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {
    // Messages the receiver deleted (deletedByReceiver) are hidden from the receiver only.
    @Query("""
        SELECT DISTINCT u
        FROM Message m
        JOIN m.sender u
        WHERE m.receiver = :receiver AND (m.deletedByReceiver = false OR m.deletedByReceiver IS NULL)
        UNION
        SELECT DISTINCT u
        FROM Message m
        JOIN m.receiver u
        WHERE m.sender = :receiver
    """)
    List<User> findDistinctByReceiver(User receiver);

    /** The thread between two people as `receiver` sees it. */
    @Query("select m from Message m " +
            "where (m.sender = :receiver and m.receiver = :sender) " +
            "or (m.sender = :sender and m.receiver = :receiver " +
            "    and (m.deletedByReceiver = false or m.deletedByReceiver is null)) " +
            "order by m.sentAt asc")
    List<Message> findMessageThread(User receiver, User sender);

    @Query("select m from Message m where m.sender = :user " +
            "or (m.receiver = :user and (m.deletedByReceiver = false or m.deletedByReceiver is null)) " +
            "order by m.sentAt desc")
    List<Message> findAllInvolving(User user);

    @Query("select m from Message m where m.id in :ids and (m.sender = :user or m.receiver = :user)")
    List<Message> findAllByIdInvolving(User user, List<UUID> ids);

    @Modifying
    @Query("update Message m set m.read = true, m.receivedAt = :now " +
            "where m.sender = :sender and m.receiver = :receiver and (m.read = false or m.read is null)")
    int markThreadRead(User sender, User receiver, ZonedDateTime now);

    void deleteAllBySender(User sender);

    void deleteAllByReceiver(User receiver);
}
