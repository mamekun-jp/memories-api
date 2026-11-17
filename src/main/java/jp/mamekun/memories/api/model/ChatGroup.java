package jp.mamekun.memories.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZonedDateTime;
import java.util.UUID;

/** A group chat. Its members are {@link GroupMember} rows, its messages {@link GroupMessage} rows. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "chat_group")
public class ChatGroup {
    public static final int MAX_NAME_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(length = MAX_NAME_LENGTH, nullable = false)
    private String name;

    /** The group's owner: always an admin, and the only one who can make or unmake admins. */
    private UUID createdBy;
    private ZonedDateTime createdAt;

    public ChatGroup(String name, UUID createdBy, ZonedDateTime createdAt) {
        this.name = name;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }
}
