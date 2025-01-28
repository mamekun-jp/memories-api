package jp.mamekun.memories.api.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PostResponse {
    private String id;
    private String ownerUid;
    private String caption;
    private Integer likes;
    private Integer comments;
    private String imageUrl;
    private String postType;
    private String timestamp;
    private String profileImageUrl;
    private String username;
}
