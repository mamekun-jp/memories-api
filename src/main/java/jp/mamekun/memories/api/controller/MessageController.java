package jp.mamekun.memories.api.controller;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jp.mamekun.memories.api.model.Message;
import jp.mamekun.memories.api.model.User;
import jp.mamekun.memories.api.model.api.ConversationResponse;
import jp.mamekun.memories.api.model.api.MessageRequest;
import jp.mamekun.memories.api.model.api.MessageResponse;
import jp.mamekun.memories.api.model.api.UserResponse;
import jp.mamekun.memories.api.model.enums.NotificationTypeEnum;
import jp.mamekun.memories.api.repository.BlockRepository;
import jp.mamekun.memories.api.repository.MessageRepository;
import jp.mamekun.memories.api.repository.UserRepository;
import jp.mamekun.memories.api.service.NotificationService;
import jp.mamekun.memories.api.util.JwtTokenUtil;
import jp.mamekun.memories.api.util.TimeFormat;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/message")
public class MessageController {
    private final JwtTokenUtil jwtTokenUtil;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;
    private final BlockRepository blockRepository;
    private final NotificationService notificationService;

    public MessageController(
            JwtTokenUtil jwtTokenUtil, UserRepository userRepository, MessageRepository messageRepository,
            BlockRepository blockRepository, NotificationService notificationService
    ) {
        this.jwtTokenUtil = jwtTokenUtil;
        this.userRepository = userRepository;
        this.messageRepository = messageRepository;
        this.blockRepository = blockRepository;
        this.notificationService = notificationService;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<MessageResponse> newMessage(
            @RequestBody @Valid MessageRequest messageRequest, @RequestHeader("Authorization") String authorizationHeader
    ) {
        User user = jwtTokenUtil.getUserFromToken(userRepository, authorizationHeader);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Optional<User> receiverOpt = userRepository.findById(UUID.fromString(messageRequest.getReceiver()));
        if (receiverOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        User receiver = receiverOpt.get();

        // Messages to someone who blocked you are silently dropped.
        if (blockRepository.existsByUserIdAndBlockedUserId(receiver.getId(), user.getId())) {
            return ResponseEntity.status(HttpStatus.CREATED).build();
        }

        boolean secured = Boolean.TRUE.equals(messageRequest.getSecured());
        Message message = messageRepository.save(new Message(
                user, receiver, messageRequest.getContent(), false, secured, ZonedDateTime.now()
        ));

        // Never put encrypted or private text into the push itself.
        notificationService.notifyUser(user, receiver.getId(), user.getId(), NotificationTypeEnum.NEW_MESSAGE,
                user.getUsername(), "Sent you a message");

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(message));
    }

    /** People you have a conversation with (kept for older app versions). */
    @GetMapping
    public ResponseEntity<List<UserResponse>> getMessages(
            @RequestHeader("Authorization") String authorizationHeader
    ) {
        User user = jwtTokenUtil.getUserFromToken(userRepository, authorizationHeader);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(messageRepository.findDistinctByReceiver(user).stream()
                .map(UserResponse::summary).toList());
    }

    /** Inbox: one row per person, newest conversation first, with last message and unread count. */
    @GetMapping("/conversations")
    public ResponseEntity<List<ConversationResponse>> getConversations(
            @RequestHeader("Authorization") String authorizationHeader
    ) {
        User user = jwtTokenUtil.getUserFromToken(userRepository, authorizationHeader);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Messages come newest first, so the first one seen per person is the latest.
        Map<UUID, Message> latest = new LinkedHashMap<>();
        Map<UUID, Integer> unread = new LinkedHashMap<>();
        for (Message m : messageRepository.findAllInvolving(user)) {
            boolean incoming = m.getReceiver().getId().equals(user.getId());
            User other = incoming ? m.getSender() : m.getReceiver();
            latest.putIfAbsent(other.getId(), m);
            if (incoming && !Boolean.TRUE.equals(m.getRead())) {
                unread.merge(other.getId(), 1, Integer::sum);
            }
        }

        List<ConversationResponse> response = new ArrayList<>();
        latest.forEach((otherId, m) -> {
            User other = m.getSender().getId().equals(otherId) ? m.getSender() : m.getReceiver();
            response.add(new ConversationResponse(
                    UserResponse.summary(other), m.getContent(), m.getSender().getId().toString(),
                    TimeFormat.iso(m.getSentAt()), Boolean.TRUE.equals(m.getSecured()),
                    unread.getOrDefault(otherId, 0)));
        });
        return ResponseEntity.ok(response);
    }

    /** The full thread with one person. Reading it marks their messages to you as read. */
    @GetMapping("/with/{senderId}")
    @Transactional
    public ResponseEntity<List<MessageResponse>> getMessagesWithSender(
            @PathVariable String senderId, @RequestHeader("Authorization") String authorizationHeader
    ) {
        User user = jwtTokenUtil.getUserFromToken(userRepository, authorizationHeader);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User other = new User(UUID.fromString(senderId));
        List<MessageResponse> thread = messageRepository.findMessageThread(user, other)
                .stream().map(MessageController::toResponse).toList();
        messageRepository.markThreadRead(other, user, ZonedDateTime.now());
        return ResponseEntity.ok(thread);
    }

    @DeleteMapping("/{messageId}")
    @Transactional
    public ResponseEntity<String> deleteMessage(
            @PathVariable String messageId, @RequestHeader("Authorization") String authorizationHeader
    ) {
        User user = jwtTokenUtil.getUserFromToken(userRepository, authorizationHeader);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        messageRepository.deleteMessageBySenderAndId(user, UUID.fromString(messageId));

        return ResponseEntity.ok("Message deleted.");
    }

    private static MessageResponse toResponse(Message message) {
        return new MessageResponse(
                message.getId().toString(), message.getContent(), message.getSender().getId().toString(),
                TimeFormat.iso(message.getSentAt()), message.getRead(), message.getSecured());
    }
}
