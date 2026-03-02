package jp.mamekun.memories.api.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@ConditionalOnProperty(
        name = "app.push-enabled",
        havingValue = "false",
        matchIfMissing = true
)
public class NoOpPushNotificationService implements PushNotificationService {

    @Override
    public CompletableFuture<String> sendPush(String token, String title, String body) {
        return CompletableFuture.completedFuture("Push disabled");
    }
}
