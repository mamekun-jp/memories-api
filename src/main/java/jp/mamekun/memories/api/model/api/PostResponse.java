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
    private Boolean isLiked;
    private Boolean isSaved;
    private Boolean isHighlighted;

    public static PostResponse from(PostDetailsProjection p) {
        return new PostResponse(
                p.getPostId(), p.getOwnerId(), p.getCaption(), p.getLikeCount(), p.getCommentCount(),
                p.getImageUrl(), p.getPostType(), p.getTimestamp(), p.getProfileImageUrl(), p.getUsername(),
                Boolean.TRUE.equals(p.getLiked()), Boolean.TRUE.equals(p.getSaved()),
                Boolean.TRUE.equals(p.getHighlighted())
        );
    }
}
