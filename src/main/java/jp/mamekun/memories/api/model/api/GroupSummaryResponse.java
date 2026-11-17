package jp.mamekun.memories.api.model.api;

import lombok.AllArgsConstructor;
import lombok.Data;

/** One group in the chat list, with a preview of its latest message. */
@Data
@AllArgsConstructor
public class GroupSummaryResponse {
    private String id;
    private String name;
    private Integer memberCount;
    private GroupMessageResponse lastMessage;
    private Integer unreadCount;
    private String createdAt;
}
