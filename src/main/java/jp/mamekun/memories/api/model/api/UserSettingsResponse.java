package jp.mamekun.memories.api.model.api;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserSettingsResponse {
    /** Whether "Seen" is shared. Off means you don't see it from others either. */
    private boolean readReceipts;
    /** Space admins can invite people. */
    private Boolean isAdmin;
    private String spaceName;
    private boolean inviteOnly;
}
