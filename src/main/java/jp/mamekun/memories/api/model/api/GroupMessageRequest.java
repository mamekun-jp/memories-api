package jp.mamekun.memories.api.model.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * A new group message: plaintext in {@code content}, or, when {@code secured}, one encrypted
 * copy per member (the sender included) in {@code copies}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupMessageRequest {
    private String content;
    private Boolean secured;
    private List<Copy> copies;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Copy {
        private String recipient;
        private String content;
    }
}
