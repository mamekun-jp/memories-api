package jp.mamekun.memories.api.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotificationResponse {
    private String id;
    private String targetId;
    private String ownerUid;
    private String content;
    private String timestamp;
    private String profileImageUrl;
    private String username;
}
