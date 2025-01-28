package jp.mamekun.memories.api.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CommentResponse {
    private String id;
    private String postId;
    private String ownerUid;
    private String content;
    private String timestamp;
    private String profileImageUrl;
    private String username;
}
