package jp.mamekun.memories.api.controller;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jp.mamekun.memories.api.model.Message;
import jp.mamekun.memories.api.model.Notification;
import jp.mamekun.memories.api.model.User;
import jp.mamekun.memories.api.model.api.MessageRequest;
import jp.mamekun.memories.api.model.api.MessageResponse;
import jp.mamekun.memories.api.model.api.UserResponse;
import jp.mamekun.memories.api.model.enums.NotificationTypeEnum;
import jp.mamekun.memories.api.repository.MessageRepository;
import jp.mamekun.memories.api.repository.NotificationRepository;
import jp.mamekun.memories.api.repository.UserRepository;
import jp.mamekun.memories.api.util.JwtTokenUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/message")
public class MessageController {
    private final JwtTokenUtil jwtTokenUtil;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;
    private final NotificationRepository notificationRepository;

    public MessageController(
            JwtTokenUtil jwtTokenUtil, UserRepository userRepository,
            MessageRepository messageRepository, NotificationRepository notificationRepository
    ) {
        this.jwtTokenUtil = jwtTokenUtil;
        this.userRepository = userRepository;
        this.messageRepository = messageRepository;
        this.notificationRepository = notificationRepository;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<String> newMessage(
            @RequestBody @Valid MessageRequest messageRequest, @RequestHeader("Authorization") String authorizationHeader
    ) {
        User user = jwtTokenUtil.getUserFromToken(userRepository, authorizationHeader);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UUID receiverId = UUID.fromString(messageRequest.getReceiver());
        Boolean secured = false;
        if (messageRequest.getSecured() != null) {
            secured = messageRequest.getSecured();
        }

        Message message = new Message(
                user, new User(receiverId), messageRequest.getContent(),
                false, secured, ZonedDateTime.now()
        );

        messageRepository.save(message);

        Notification notification = new Notification();
        notification.setTargetId(user.getId());
        notification.setContent(NotificationTypeEnum.NEW_MESSAGE.name());
        notification.setReceiver(receiverId);
        notification.setIsPublic(false);
        notification.setTimestamp(ZonedDateTime.now());
        notification.setOwner(user);
        notificationRepository.save(notification);

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> getMessages(
            @RequestHeader("Authorization") String authorizationHeader
    ) {
        User user = jwtTokenUtil.getUserFromToken(userRepository, authorizationHeader);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(messageRepository.findDistinctByReceiver(user).stream().map(sender ->
                new UserResponse(sender.getId().toString(), sender.getUsername(), sender.getProfileImageUrl(),
                sender.getFullName(), sender.getBio(), "", false, 0, 0, 0)
        ).toList());
    }

    @GetMapping("/with/{senderId}")
    public ResponseEntity<List<MessageResponse>> getMessagesWithSender(
            @PathVariable("senderId") String senderId, @RequestHeader("Authorization") String authorizationHeader
    ) {
        User user = jwtTokenUtil.getUserFromToken(userRepository, authorizationHeader);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(messageRepository.findMessageThread(user, new User(UUID.fromString(senderId)))
                .stream().map(message -> new MessageResponse(
                        message.getId().toString(), message.getContent(), message.getSender().getId().toString(),
                        message.getSentAt().toString(), message.getRead(), message.getSecured()
                )).toList()
        );
    }

    @DeleteMapping("/{messageId}")
    @Transactional
    public ResponseEntity<String> deleteMessage(
            @PathVariable("messageId") String messageId, @RequestHeader("Authorization") String authorizationHeader
    ) {
        User user = jwtTokenUtil.getUserFromToken(userRepository, authorizationHeader);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        messageRepository.deleteMessageBySenderAndId(user, UUID.fromString(messageId));

        return ResponseEntity.ok("Message deleted.");
    }
}
