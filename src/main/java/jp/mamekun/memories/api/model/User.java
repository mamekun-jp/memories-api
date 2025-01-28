package jp.mamekun.memories.api.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "app_user")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private Boolean isDeleted;
    private Boolean isApproved;

    @NotBlank
    private String username;

    @Email
    @NotBlank
    private String email;

    @NotBlank
    private String password;

    private String profileImageUrl;
    private String fullName;
    private String bio;
    private ZonedDateTime lastNotificationCheck;

    public User(Boolean isApproved, boolean isDeleted, String username, String email, String password) {
        this.isApproved = isApproved;
        this.isDeleted = isDeleted;
        this.username = username;
        this.email = email;
        this.password = password;
    }

    public User(UUID id) {
        this.id = id;
    }
}
