package jp.mamekun.memories.api.model.api;

import lombok.Data;

@Data
public class InviteRequest {
    /** How long the invite stays valid, in hours (1 hour to 30 days, default 3 days). */
    private Integer expiresInHours;
}
