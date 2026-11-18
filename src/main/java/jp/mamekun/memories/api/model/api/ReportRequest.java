package jp.mamekun.memories.api.model.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Body for reporting someone: why ({@code reason}) and where from ({@code context}: PROFILE or CHAT). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportRequest {
    private String reason;
    private String context;
}
