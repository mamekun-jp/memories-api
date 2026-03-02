package jp.mamekun.memories.api.service.impl;

import com.eatthepath.pushy.apns.ApnsClient;
import com.eatthepath.pushy.apns.DeliveryPriority;
import com.eatthepath.pushy.apns.PushType;
import com.eatthepath.pushy.apns.util.SimpleApnsPushNotification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jp.mamekun.memories.api.service.PushNotificationService;
import jp.mamekun.memories.api.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@ConditionalOnProperty(
        name = "app.push-enabled",
        havingValue = "true"
)
public class PushNotificationServiceImpl implements PushNotificationService {

    private final ApnsClient apnsClient;
    private final UserService userService;

    @Value("${apns.bundle-id}")
    private String bundleId;

    public PushNotificationServiceImpl(ApnsClient apnsClient,  UserService userService) {
        this.apnsClient = apnsClient;
        this.userService = userService;
    }

    @Override
    public CompletableFuture<String> sendPush(
            String deviceToken,
            String title,
            String body
    ) throws JsonProcessingException {

        ObjectMapper mapper = new ObjectMapper();

        Map<String, Object> payloadMap = Map.of(
                "aps", Map.of(
                        "alert", Map.of(
                                "title", title,
                                "body", body
                        ),
                        "sound", "default"
                )
        );

        String payload = mapper.writeValueAsString(payloadMap);

        SimpleApnsPushNotification pushNotification =
                new SimpleApnsPushNotification(
                        deviceToken,
                        bundleId,
                        payload,
                        Instant.now().plusSeconds(3600),
                        DeliveryPriority.IMMEDIATE,
                        PushType.ALERT,
                        null,
                        null
                );

        return apnsClient.sendNotification(pushNotification)
                .thenApply(response -> {

                    if (response.isAccepted()) {
                        return "Accepted by APNs";
                    }

                    String reason = response.getRejectionReason()
                            .orElse("Unknown rejection");

                    if (response.getTokenInvalidationTimestamp().isPresent()) {
                        userService.nullifyToken(deviceToken);
                        return "Token invalid. Reason: " + reason;
                    }

                    return "Rejected: " + reason;

                })
                .exceptionally(ex -> "Push failed: " + ex.getMessage());
    }

    public CompletableFuture<Void> sendBroadcast(List<String> tokens, String title, String body) {
        List<CompletableFuture<String>> futures = tokens.stream()
                .map(token -> {
                    try {
                        return sendPush(token, title, body);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                })
                .toList();

        return CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
        );
    }
}