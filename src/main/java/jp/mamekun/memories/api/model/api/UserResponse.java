package jp.mamekun.memories.api.model.api;

import jp.mamekun.memories.api.model.User;
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

    /** Public fields only, for lists (likes, followers, viewers...). Email is left blank on purpose. */
    public static UserResponse summary(User user) {
        return new UserResponse(user.getId().toString(), user.getUsername(), user.getProfileImageUrl(),
                user.getFullName(), user.getBio(), "", false, 0, 0, 0);
    }
}
