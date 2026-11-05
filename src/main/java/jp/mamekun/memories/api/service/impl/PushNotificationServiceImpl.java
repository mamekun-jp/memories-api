package jp.mamekun.memories.api.service.impl;

import com.eatthepath.pushy.apns.ApnsClient;
import com.eatthepath.pushy.apns.DeliveryPriority;
import com.eatthepath.pushy.apns.PushType;
import com.eatthepath.pushy.apns.util.SimpleApnsPushNotification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jp.mamekun.memories.api.service.PushMessage;
import jp.mamekun.memories.api.service.PushNotificationService;
import jp.mamekun.memories.api.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
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
    private final ObjectMapper objectMapper = new ObjectMapper();

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
        return sendPush(deviceToken, PushMessage.alert(title, body, null, null, null));
    }

    @Override
    public CompletableFuture<String> sendPush(
            String deviceToken,
            PushMessage message
    ) throws JsonProcessingException {

        String payload = objectMapper.writeValueAsString(buildPayload(message));

        SimpleApnsPushNotification pushNotification =
                new SimpleApnsPushNotification(
                        deviceToken,
                        bundleId,
                        payload,
                        Instant.now().plusSeconds(3600),
                        message.background() ? DeliveryPriority.CONSERVE_POWER : DeliveryPriority.IMMEDIATE,
                        message.background() ? PushType.BACKGROUND : PushType.ALERT,
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

    @Override
    public CompletableFuture<String> sendBackgroundPush(String deviceToken) throws JsonProcessingException {
        return sendPush(deviceToken, PushMessage.silent());
    }

    /**
     * Builds the APNs JSON payload. Alert pushes get {@code aps.alert}, a sound and
     * {@code mutable-content} so a notification service extension can modify them, plus
     * top-level routing fields the app reads when the notification is tapped.
     * Background pushes only set {@code aps.content-available}.
     */
    private Map<String, Object> buildPayload(PushMessage message) {
        Map<String, Object> aps = new LinkedHashMap<>();
        if (message.background()) {
            aps.put("content-available", 1);
        } else {
            aps.put("alert", Map.of(
                    "title", message.title(),
                    "body", message.body()
            ));
            aps.put("sound", "default");
            aps.put("mutable-content", 1);
        }

        Map<String, Object> payloadMap = new LinkedHashMap<>();
        payloadMap.put("aps", aps);
        if (!message.background()) {
            if (message.type() != null) {
                payloadMap.put("type", message.type());
            }
            if (message.targetId() != null) {
                payloadMap.put("targetId", message.targetId());
            }
            if (message.actorId() != null) {
                payloadMap.put("actorId", message.actorId());
            }
        }
        return payloadMap;
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
