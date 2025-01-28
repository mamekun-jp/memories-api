package jp.mamekun.memories.api.model.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserRequest {
    private String id;
    private String username;
    private String profileImageUrl;
    private String fullName;
    private String bio;
    private String email;
}
