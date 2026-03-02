package jp.mamekun.memories.api.job;

import jp.mamekun.memories.api.repository.PasswordResetTokenRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;

@Component
public class PasswordResetTokenCleanupJob {

    private final PasswordResetTokenRepository passwordResetTokenRepository;

    public PasswordResetTokenCleanupJob(PasswordResetTokenRepository passwordResetTokenRepository) {
        this.passwordResetTokenRepository = passwordResetTokenRepository;
    }

    /**
     * Runs every day at 03:15 server time.
     * Adjust the cron as needed.
     */
    @Scheduled(cron = "${app.password-reset.cleanup-cron:0 15 3 * * *}")
    public void cleanupExpiredTokens() {
        ZonedDateTime now = ZonedDateTime.now();
        passwordResetTokenRepository.deleteByExpiresAtBefore(now);
    }
}
