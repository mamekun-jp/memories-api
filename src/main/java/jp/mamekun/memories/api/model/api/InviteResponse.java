package jp.mamekun.memories.api.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InviteResponse {
    private String token;
    private String createdAt;
    private String expiresAt;
    /** ACTIVE, USED, EXPIRED or REVOKED. */
    private String status;
    /** Username of the account created with the invite, once used. */
    private String usedBy;
}
