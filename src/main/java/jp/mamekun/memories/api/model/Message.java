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
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "user_message")
public class Message {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @EqualsAndHashCode.Include
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sender_id")
    private User sender;

    @EqualsAndHashCode.Include
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "receiver_uid")
    private User receiver;

    @Column(length = 2048)
    private String content;

    private Boolean read;
    private Boolean secured;
    private ZonedDateTime sentAt;
    private ZonedDateTime receivedAt;

    public Message(User sender, User receiver, String content, Boolean read, Boolean secured, ZonedDateTime sentAt) {
        this.sender = sender;
        this.receiver = receiver;
        this.content = content;
        this.read = read;
        this.sentAt = sentAt;
    }
}
