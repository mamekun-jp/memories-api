package jp.mamekun.memories.api.model.api;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserResponse {
    private String id;
    private String username;
    private String profileImageUrl;
    private String fullName;
    private String bio;
    private String email;
    private Boolean isFollowing;
    private Integer followers;
    private Integer following;
    private Integer posts;
}
