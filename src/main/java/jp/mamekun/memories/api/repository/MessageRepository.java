package jp.mamekun.memories.api.repository;

import jp.mamekun.memories.api.model.Message;
import jp.mamekun.memories.api.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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
            "or (m.sender = :sender and m.receiver = :receiver)")
    List<Message> findMessageThread(User receiver, User sender);

    void deleteMessageBySenderAndId(User sender, UUID id);
}
