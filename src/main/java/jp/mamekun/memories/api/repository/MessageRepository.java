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
    @Query("""
        SELECT DISTINCT u
        FROM Message m
        JOIN m.sender u
        WHERE m.receiver = :receiver
        UNION
        SELECT DISTINCT u
        FROM Message m
        JOIN m.receiver u
        WHERE m.sender = :receiver
    """)
    List<User> findDistinctByReceiver(User receiver);

    @Query("select m from Message m " +
            "where (m.sender = :receiver and m.receiver = :sender) " +
            "or (m.sender = :sender and m.receiver = :receiver) " +
            "order by m.sentAt asc")
    List<Message> findMessageThread(User receiver, User sender);

    @Query("select m from Message m where m.sender = :user or m.receiver = :user order by m.sentAt desc")
    List<Message> findAllInvolving(User user);

    @Modifying
    @Query("update Message m set m.read = true, m.receivedAt = :now " +
            "where m.sender = :sender and m.receiver = :receiver and (m.read = false or m.read is null)")
    int markThreadRead(User sender, User receiver, ZonedDateTime now);

    void deleteMessageBySenderAndId(User sender, UUID id);

    void deleteAllBySender(User sender);

    void deleteAllByReceiver(User receiver);
}
