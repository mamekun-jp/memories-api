package jp.mamekun.memories.api.model;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
public class Comment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID postId;
    private Boolean isDeleted;
    private String content;
    private ZonedDateTime timestamp;

    @EqualsAndHashCode.Include
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "owner_uid")
    private User owner;
}
