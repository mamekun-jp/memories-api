package jp.mamekun.memories.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jp.mamekun.memories.api.model.enums.GroupEventEnum;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * A message in a group chat, or a notice such as "alice added bob" (then {@code event} is set).
 *
 * <p>End-to-end encrypted messages ({@code secured}) have no {@code content}: the sender encrypts
 * the text once per member, and each copy is a {@link GroupMessageCopy}.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "chat_group_message")
public class GroupMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    /** Who wrote the message, or who did what a notice describes. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sender_id")
    private User sender;

    /** Plaintext, the group's new name for RENAMED, or null for encrypted messages. */
    @Column(length = 8192)
    private String content;

    private Boolean secured;

    @Enumerated(EnumType.STRING)
    private GroupEventEnum event;

    /** The person a notice is about (who was added, removed, made admin...). */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "target_user_id")
    private User targetUser;

    private ZonedDateTime sentAt;

    public static GroupMessage text(UUID groupId, User sender, String content, boolean secured, ZonedDateTime sentAt) {
        GroupMessage message = new GroupMessage();
        message.groupId = groupId;
        message.sender = sender;
        message.content = content;
        message.secured = secured;
        message.sentAt = sentAt;
        return message;
    }

    public static GroupMessage event(UUID groupId, User actor, GroupEventEnum event, User target, String content,
                                     ZonedDateTime sentAt) {
        GroupMessage message = text(groupId, actor, content, false, sentAt);
        message.event = event;
        message.targetUser = target;
        return message;
    }
}
