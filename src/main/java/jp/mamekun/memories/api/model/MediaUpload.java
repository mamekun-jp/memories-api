package jp.mamekun.memories.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZonedDateTime;
import java.util.UUID;

/** Who uploaded a media file, so nobody can post or reuse someone else's upload as their own. */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class MediaUpload {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** The relative url handed out by the upload endpoint, e.g. {@code image/<uuid>.jpg}. */
    @Column(unique = true, nullable = false)
    private String url;

    @Column(nullable = false)
    private UUID ownerId;

    private ZonedDateTime createdAt;

    public MediaUpload(String url, UUID ownerId, ZonedDateTime createdAt) {
        this.url = url;
        this.ownerId = ownerId;
        this.createdAt = createdAt;
    }
}
