package jp.mamekun.memories.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZonedDateTime;
import java.util.UUID;

/** Someone in a group chat. Members only see the messages sent since they joined. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "chat_group_member", uniqueConstraints = @UniqueConstraint(columnNames = {"group_id", "user_id"}))
public class GroupMember {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id")
    private User user;

    /** Admins can add and remove members. */
    private Boolean admin;
    private ZonedDateTime joinedAt;
    /** Messages sent up to this time count as read by this member. */
    private ZonedDateTime lastReadAt;

    public GroupMember(UUID groupId, User user, boolean admin, ZonedDateTime joinedAt) {
        this.groupId = groupId;
        this.user = user;
        this.admin = admin;
        this.joinedAt = joinedAt;
        this.lastReadAt = joinedAt;
    }

    public boolean isAdmin() {
        return Boolean.TRUE.equals(admin);
    }
}
