package jp.mamekun.memories.api.model.api;

import lombok.AllArgsConstructor;
import lombok.Data;

/** One row in the inbox: the other person plus a preview of the latest message. */
@Data
@AllArgsConstructor
public class ConversationResponse {
    private UserResponse user;
    private String lastMessage;
    private String lastMessageSender;
    private String lastMessageTimestamp;
    private Boolean lastMessageSecured;
    private Integer unreadCount;
}
