package jp.mamekun.memories.api.controller;

import jakarta.transaction.Transactional;
import jp.mamekun.memories.api.model.User;
import jp.mamekun.memories.api.model.api.NotificationCountResponse;
import jp.mamekun.memories.api.model.api.NotificationResponse;
import jp.mamekun.memories.api.repository.NotificationRepository;
import jp.mamekun.memories.api.repository.UserRepository;
import jp.mamekun.memories.api.util.JwtTokenUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/notification")
public class NotificationController {
    private final JwtTokenUtil jwtTokenUtil;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationController(
            JwtTokenUtil jwtTokenUtil, NotificationRepository notificationRepository, UserRepository userRepository
    ) {
        this.jwtTokenUtil = jwtTokenUtil;
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/check")
    @Transactional
    public ResponseEntity<NotificationCountResponse> getNotifications(
            @RequestHeader("Authorization") String authorizationHeader
    ) {
        User user = jwtTokenUtil.getUserFromToken(userRepository, authorizationHeader);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        ZonedDateTime sinceDateTime;
        if (user.getLastNotificationCheck() != null) {
            sinceDateTime = user.getLastNotificationCheck();
        } else {
            sinceDateTime = Instant.ofEpochSecond(0).atZone(ZoneId.systemDefault());
        }

        user.setLastNotificationCheck(ZonedDateTime.now());
        userRepository.save(user);

        Integer response = notificationRepository.countAllByTimestampAndOwner(sinceDateTime, user.getId());
        return ResponseEntity.ok(new NotificationCountResponse(response));
    }

    @GetMapping("/since/{sinceStr}")
    @Transactional
    public ResponseEntity<List<NotificationResponse>> getNotifications(
            @PathVariable("sinceStr") String sinceStr, @RequestHeader("Authorization") String authorizationHeader
    ) {
        User user = jwtTokenUtil.getUserFromToken(userRepository, authorizationHeader);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (sinceStr.indexOf(".") > 0) {
            sinceStr = sinceStr.substring(0, sinceStr.indexOf("."));
        }

        // Convert to ZonedDateTime
        long since;
        try {
            since = Long.parseLong(sinceStr);
        } catch (NumberFormatException e) {
            since = 0;
        }
        ZonedDateTime sinceDateTime = Instant.ofEpochSecond(since)
                .atZone(ZoneId.systemDefault());

        List<NotificationResponse> response = notificationRepository.findAllForUser(sinceDateTime, user.getId())
                .stream().map(notification -> new NotificationResponse(
                        notification.getId().toString(),
                        notification.getTargetId().toString(),
                        notification.getOwner().getId().toString(), notification.getContent(),
                        notification.getTimestamp().toString(), notification.getOwner().getProfileImageUrl(),
                        notification.getOwner().getUsername())
                ).toList();
        return ResponseEntity.ok(response);
    }
}
