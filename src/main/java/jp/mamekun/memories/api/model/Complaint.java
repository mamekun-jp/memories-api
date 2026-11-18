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

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Complaint {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID userId;
    /** The reported post, for post reports. */
    private UUID postId;
    /** The reported person, for reports about someone (from their profile or a chat). */
    private UUID reportedUserId;
    /** What the report is about, e.g. SPAM or HARASSMENT, as chosen in the app. */
    @Column(length = 40)
    private String reason;
    /** Where it was reported from: PROFILE or CHAT. */
    @Column(length = 20)
    private String context;
    private ZonedDateTime timestamp;

    public Complaint(UUID userId, UUID postId, ZonedDateTime timestamp) {
        this.userId = userId;
        this.postId = postId;
        this.timestamp = timestamp;
    }

    public static Complaint aboutUser(UUID userId, UUID reportedUserId, String reason, String context) {
        Complaint complaint = new Complaint(userId, null, ZonedDateTime.now());
        complaint.setReportedUserId(reportedUserId);
        complaint.setReason(reason);
        complaint.setContext(context);
        return complaint;
    }
}
