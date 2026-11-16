package jp.mamekun.memories.api.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;

/** What the app may know about an invite before signing up (no login needed). */
@Data
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InviteInfoResponse {
    private boolean valid;
    private String spaceName;
    private String expiresAt;
}
