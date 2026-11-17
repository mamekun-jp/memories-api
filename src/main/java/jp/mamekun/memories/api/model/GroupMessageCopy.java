package jp.mamekun.memories.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** One member's copy of an end-to-end encrypted group message, encrypted for that member. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "chat_group_message_copy", indexes = @Index(columnList = "message_id, recipient_id"))
public class GroupMessageCopy {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    @Column(length = 8192)
    private String content;

    public GroupMessageCopy(UUID messageId, UUID recipientId, String content) {
        this.messageId = messageId;
        this.recipientId = recipientId;
        this.content = content;
    }
}
