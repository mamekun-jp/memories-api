package jp.mamekun.memories.api.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * A group message as one member sees it. For encrypted messages {@code content} is that member's
 * own copy. Notices have {@code event} set (and {@code targetUser}/{@code targetName} when they
 * are about someone).
 */
@Data
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GroupMessageResponse {
    private String id;
    private String sender;
    private String username;
    private String profileImageUrl;
    private String content;
    private String timestamp;
    private Boolean secured;
    private Boolean isRead;
    private String event;
    private String targetUser;
    private String targetName;
}
