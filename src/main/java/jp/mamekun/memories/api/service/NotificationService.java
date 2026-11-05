package jp.mamekun.memories.api.service;

import jp.mamekun.memories.api.model.Notification;
import jp.mamekun.memories.api.model.User;
import jp.mamekun.memories.api.model.enums.NotificationTypeEnum;
import jp.mamekun.memories.api.repository.NotificationRepository;
import jp.mamekun.memories.api.repository.UserRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Stores in-app notifications and, when push is enabled, sends the matching APNs push.
 * Push failures are logged and never break the request that triggered them.
 */
@Service
@Log4j2
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final PushNotificationService pushNotificationService;

    @Value("${app.push-enabled:false}")
    private boolean pushEnabled;

    public NotificationService(NotificationRepository notificationRepository, UserRepository userRepository,
                               PushNotificationService pushNotificationService) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.pushNotificationService = pushNotificationService;
    }

    /** Notify one person about something {@code actor} did. Does nothing if they are the same person. */
    public void notifyUser(User actor, UUID receiverId, UUID targetId, NotificationTypeEnum type,
                           String pushTitle, String pushBody) {
        if (receiverId == null || receiverId.equals(actor.getId())) {
            return;
        }
        save(actor, receiverId, targetId, type, false);
        userRepository.findById(receiverId).ifPresent(receiver -> push(receiver.getDeviceToken(), pushTitle, pushBody));
    }

    /** Notify everyone on the server (used for new posts and stories). */
    public void notifyEveryone(User actor, UUID targetId, NotificationTypeEnum type, String pushTitle, String pushBody) {
        save(actor, null, targetId, type, true);
        if (!pushEnabled) {
            return;
        }
        userRepository.findAll().stream()
                .filter(u -> !u.getId().equals(actor.getId()))
                .map(User::getDeviceToken)
                .filter(Objects::nonNull)
                .forEach(token -> push(token, pushTitle, pushBody));
    }

    private void save(User actor, UUID receiverId, UUID targetId, NotificationTypeEnum type, boolean isPublic) {
        Notification notification = new Notification();
        notification.setOwner(actor);
        notification.setReceiver(receiverId);
        notification.setTargetId(targetId);
        notification.setContent(type.name());
        notification.setIsPublic(isPublic);
        notification.setTimestamp(ZonedDateTime.now());
        notificationRepository.save(notification);
    }

    private void push(String deviceToken, String title, String body) {
        if (!pushEnabled || deviceToken == null || deviceToken.isBlank()) {
            return;
        }
        try {
            pushNotificationService.sendPush(deviceToken, title, body)
                    .thenAccept(result -> log.debug("Push result: {}", result));
        } catch (Exception e) {
            log.error("Failed to send push notification", e);
        }
    }
}
