package jp.mamekun.memories.api.model.api;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MessageResponse {
    private String id;
    private String content;
    private String sender;
    private String timestamp;
    private Boolean isRead;
    private Boolean secured;
}
