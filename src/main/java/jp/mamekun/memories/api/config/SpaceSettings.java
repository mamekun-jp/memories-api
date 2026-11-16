package jp.mamekun.memories.api.config;

import jp.mamekun.memories.api.model.User;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Space-wide settings from the environment (.env): name, admins and how people can join. */
@Component
@Getter
public class SpaceSettings {
    /** Shown on invite pages and in the app when someone joins (APP_SPACE_NAME). */
    private final String spaceName;
    /** When true, new accounts need a valid invite (APP_INVITE_ONLY). */
    private final boolean inviteOnly;
    /** Where the invite page sends people who don't have the app yet (APP_STORE_URL). */
    private final String appStoreUrl;
    private final Set<String> adminEmails;

    public SpaceSettings(
            @Value("${app.space-name:Memories}") String spaceName,
            @Value("${app.invite-only:false}") boolean inviteOnly,
            @Value("${app.app-store-url:https://apps.apple.com/app/id6740176333}") String appStoreUrl,
            @Value("${app.admin-emails:}") String adminEmails
    ) {
        this.spaceName = spaceName;
        this.inviteOnly = inviteOnly;
        this.appStoreUrl = appStoreUrl;
        // APP_ADMIN_EMAILS: comma-separated account emails of the space admins.
        this.adminEmails = Arrays.stream(adminEmails.split(","))
                .map(email -> email.trim().toLowerCase(Locale.ROOT))
                .filter(email -> !email.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isAdmin(User user) {
        return user != null && isAdminEmail(user.getEmail());
    }

    public boolean isAdminEmail(String email) {
        return email != null && adminEmails.contains(email.trim().toLowerCase(Locale.ROOT));
    }
}
