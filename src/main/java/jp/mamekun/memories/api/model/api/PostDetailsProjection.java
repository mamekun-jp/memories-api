package jp.mamekun.memories.api.model.api;

public interface PostDetailsProjection {
    String getPostId();
    String getCaption();
    String getImageUrl();
    String getPostType();
    String getTimestamp();
    String getOwnerId();
    String getUsername();
    String getProfileImageUrl();
    int getLikeCount();
    int getCommentCount();
}

