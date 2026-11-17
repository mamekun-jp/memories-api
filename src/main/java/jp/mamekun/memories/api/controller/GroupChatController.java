package jp.mamekun.memories.api.controller;

import jakarta.transaction.Transactional;
import jp.mamekun.memories.api.model.ChatGroup;
import jp.mamekun.memories.api.model.GroupMember;
import jp.mamekun.memories.api.model.GroupMessage;
import jp.mamekun.memories.api.model.GroupMessageCopy;
import jp.mamekun.memories.api.model.User;
import jp.mamekun.memories.api.model.api.DeleteMessagesRequest;
import jp.mamekun.memories.api.model.api.GroupCreateRequest;
import jp.mamekun.memories.api.model.api.GroupMessageRequest;
import jp.mamekun.memories.api.model.api.GroupMessageResponse;
import jp.mamekun.memories.api.model.api.GroupResponse;
import jp.mamekun.memories.api.model.api.GroupSummaryResponse;
import jp.mamekun.memories.api.model.api.GroupUpdateRequest;
import jp.mamekun.memories.api.model.api.UserResponse;
import jp.mamekun.memories.api.model.enums.GroupEventEnum;
import jp.mamekun.memories.api.model.enums.NotificationTypeEnum;
import jp.mamekun.memories.api.repository.ChatGroupRepository;
import jp.mamekun.memories.api.repository.GroupMemberRepository;
import jp.mamekun.memories.api.repository.GroupMessageCopyRepository;
import jp.mamekun.memories.api.repository.GroupMessageRepository;
import jp.mamekun.memories.api.repository.UserRepository;
import jp.mamekun.memories.api.service.GroupChatService;
import jp.mamekun.memories.api.service.NotificationService;
import jp.mamekun.memories.api.util.JwtTokenUtil;
import jp.mamekun.memories.api.util.TimeFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Group chats. Any member can write and rename the group; admins add and remove members; only
 * the owner (the creator, until they leave) makes or unmakes admins, and can't be removed.
 */
@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupChatController {
    private final JwtTokenUtil jwtTokenUtil;
    private final UserRepository userRepository;
    private final ChatGroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;
    private final GroupMessageRepository messageRepository;
    private final GroupMessageCopyRepository copyRepository;
    private final GroupChatService groupChatService;
    private final NotificationService notificationService;

    @Value("${app.e2e-enabled:true}")
    private boolean e2eEnabled;

    /** The requester, the group and the requester's membership; 404 when they aren't in it. */
    private record Access(User user, ChatGroup group, GroupMember member) {
        boolean isOwner() {
            return user.getId().equals(group.getCreatedBy());
        }
    }

    @PostMapping
    @Transactional
    public ResponseEntity<GroupResponse> createGroup(
            @RequestBody GroupCreateRequest request, @RequestHeader("Authorization") String authorizationHeader
    ) {
        User user = currentUser(authorizationHeader);
        String name = validName(request.getName());
        List<User> others = findUsers(request.getMemberIds()).stream()
                .filter(u -> !u.getId().equals(user.getId())).toList();
        if (others.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick at least one person.");
        }

        ZonedDateTime now = ZonedDateTime.now();
        ChatGroup group = groupRepository.save(new ChatGroup(name, user.getId(), now));
        memberRepository.save(new GroupMember(group.getId(), user, true, now));
        others.forEach(other -> memberRepository.save(new GroupMember(group.getId(), other, false, now)));
        messageRepository.save(GroupMessage.event(group.getId(), user, GroupEventEnum.CREATED, null, name, now));

        notify(user, group, others, user.getUsername() + " added you");
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(group, user));
    }

    /** The requester's groups, with the latest message and unread count, newest activity first. */
    @GetMapping
    public ResponseEntity<List<GroupSummaryResponse>> getGroups(@RequestHeader("Authorization") String authorizationHeader) {
        User user = currentUser(authorizationHeader);
        Map<GroupSummaryResponse, ZonedDateTime> lastActivity = new HashMap<>();
        for (GroupMember member : memberRepository.findAllByUser(user)) {
            groupRepository.findById(member.getGroupId()).ifPresent(group -> {
                GroupMessage last = messageRepository
                        .findFirstByGroupIdAndSentAtGreaterThanEqualOrderBySentAtDesc(group.getId(), member.getJoinedAt())
                        .orElse(null);
                int memberCount = memberRepository.findAllByGroupIdOrderByJoinedAtAsc(group.getId()).size();
                long unread = messageRepository.countUnread(group.getId(), user, member.getLastReadAt());
                GroupSummaryResponse summary = new GroupSummaryResponse(group.getId().toString(), group.getName(), memberCount,
                        last == null ? null : toMessageResponse(last, user, copiesFor(user, List.of(last)), null),
                        (int) unread, TimeFormat.iso(group.getCreatedAt()));
                lastActivity.put(summary, last != null ? last.getSentAt() : group.getCreatedAt());
            });
        }
        List<GroupSummaryResponse> response = new ArrayList<>(lastActivity.keySet());
        response.sort(Comparator.comparing(lastActivity::get, Comparator.reverseOrder()));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{groupId}")
    public ResponseEntity<GroupResponse> getGroup(
            @PathVariable String groupId, @RequestHeader("Authorization") String authorizationHeader
    ) {
        Access access = access(authorizationHeader, groupId);
        return ResponseEntity.ok(toResponse(access.group(), access.user()));
    }

    /** Renames the group (any member). */
    @PutMapping("/{groupId}")
    @Transactional
    public ResponseEntity<GroupResponse> renameGroup(
            @PathVariable String groupId, @RequestBody GroupUpdateRequest request,
            @RequestHeader("Authorization") String authorizationHeader
    ) {
        Access access = access(authorizationHeader, groupId);
        String name = validName(request.getName());
        if (!name.equals(access.group().getName())) {
            access.group().setName(name);
            groupRepository.save(access.group());
            groupChatService.addEvent(access.group(), access.user(), GroupEventEnum.RENAMED, null, name);
        }
        return ResponseEntity.ok(toResponse(access.group(), access.user()));
    }

    /** Adds people (admins only). They see the group's messages from now on. */
    @PostMapping("/{groupId}/members")
    @Transactional
    public ResponseEntity<GroupResponse> addMembers(
            @PathVariable String groupId, @RequestBody GroupUpdateRequest request,
            @RequestHeader("Authorization") String authorizationHeader
    ) {
        Access access = access(authorizationHeader, groupId);
        requireAdmin(access);
        Set<UUID> current = memberRepository.findAllByGroupIdOrderByJoinedAtAsc(access.group().getId()).stream()
                .map(m -> m.getUser().getId()).collect(Collectors.toSet());
        List<User> added = findUsers(request.getUserIds()).stream()
                .filter(u -> !current.contains(u.getId())).toList();
        for (User newcomer : added) {
            ZonedDateTime now = ZonedDateTime.now();
            memberRepository.save(new GroupMember(access.group().getId(), newcomer, false, now));
            messageRepository.save(GroupMessage.event(access.group().getId(), access.user(), GroupEventEnum.ADDED,
                    newcomer, null, now));
        }
        notify(access.user(), access.group(), added, access.user().getUsername() + " added you");
        return ResponseEntity.ok(toResponse(access.group(), access.user()));
    }

    /**
     * Removes someone. Anyone can remove themselves (leave). Admins can remove members; only the
     * owner can remove another admin; nobody can remove the owner.
     */
    @DeleteMapping("/{groupId}/members/{userId}")
    @Transactional
    public ResponseEntity<Void> removeMember(
            @PathVariable String groupId, @PathVariable String userId,
            @RequestHeader("Authorization") String authorizationHeader
    ) {
        Access access = access(authorizationHeader, groupId);
        GroupMember target = memberOf(access.group(), parseId(userId));
        boolean leaving = target.getId().equals(access.member().getId());
        if (!leaving) {
            requireAdmin(access);
            boolean targetIsOwner = target.getUser().getId().equals(access.group().getCreatedBy());
            if (targetIsOwner || (target.isAdmin() && !access.isOwner())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }
        }
        groupChatService.removeMember(access.group(), target, access.user(),
                leaving ? GroupEventEnum.LEFT : GroupEventEnum.REMOVED);
        return ResponseEntity.noContent().build();
    }

    /** Makes someone an admin or takes it away (owner only). */
    @PutMapping("/{groupId}/members/{userId}/admin")
    @Transactional
    public ResponseEntity<GroupResponse> setAdmin(
            @PathVariable String groupId, @PathVariable String userId, @RequestBody GroupUpdateRequest request,
            @RequestHeader("Authorization") String authorizationHeader
    ) {
        Access access = access(authorizationHeader, groupId);
        if (!access.isOwner()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        GroupMember target = memberOf(access.group(), parseId(userId));
        boolean admin = Boolean.TRUE.equals(request.getAdmin());
        if (target.getId().equals(access.member().getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The owner is always an admin.");
        }
        if (target.isAdmin() != admin) {
            target.setAdmin(admin);
            memberRepository.save(target);
            groupChatService.addEvent(access.group(), access.user(),
                    admin ? GroupEventEnum.MADE_ADMIN : GroupEventEnum.REMOVED_ADMIN, target.getUser(), null);
        }
        return ResponseEntity.ok(toResponse(access.group(), access.user()));
    }

    /** The messages since the requester joined. Reading them marks the group read. */
    @GetMapping("/{groupId}/messages")
    @Transactional
    public ResponseEntity<List<GroupMessageResponse>> getMessages(
            @PathVariable String groupId, @RequestHeader("Authorization") String authorizationHeader
    ) {
        Access access = access(authorizationHeader, groupId);
        List<GroupMessage> messages = messageRepository.findAllByGroupIdAndSentAtGreaterThanEqualOrderBySentAtAsc(
                access.group().getId(), access.member().getJoinedAt());
        Map<UUID, String> copies = copiesFor(access.user(), messages);
        List<GroupMember> readers = receiptReaders(access);
        List<GroupMessageResponse> response = messages.stream()
                .map(m -> toMessageResponse(m, access.user(), copies, readers)).toList();

        access.member().setLastReadAt(ZonedDateTime.now());
        memberRepository.save(access.member());
        return ResponseEntity.ok(response);
    }

    /**
     * Sends a message. An encrypted one must carry a copy for exactly the current members (sender
     * included); 409 means the members changed, so the app reloads the group and encrypts again.
     */
    @PostMapping("/{groupId}/messages")
    @Transactional
    public ResponseEntity<GroupMessageResponse> sendMessage(
            @PathVariable String groupId, @RequestBody GroupMessageRequest request,
            @RequestHeader("Authorization") String authorizationHeader
    ) {
        Access access = access(authorizationHeader, groupId);
        List<GroupMember> members = memberRepository.findAllByGroupIdOrderByJoinedAtAsc(access.group().getId());
        boolean secured = Boolean.TRUE.equals(request.getSecured());
        GroupMessage message;
        Map<UUID, String> copies = new HashMap<>();
        if (secured) {
            if (!e2eEnabled) {
                // Same as direct messages: the app falls back to plaintext.
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End-to-end encryption is off.");
            }
            for (GroupMessageRequest.Copy copy : Objects.requireNonNullElse(request.getCopies(), List.<GroupMessageRequest.Copy>of())) {
                if (copy.getContent() == null || copy.getContent().isBlank()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
                }
                copies.put(parseId(copy.getRecipient()), copy.getContent());
            }
            Set<UUID> memberIds = members.stream().map(m -> m.getUser().getId()).collect(Collectors.toSet());
            if (!copies.keySet().equals(memberIds)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "The group's members changed.");
            }
            message = messageRepository.save(GroupMessage.text(access.group().getId(), access.user(), null, true, ZonedDateTime.now()));
            copies.forEach((recipient, content) -> copyRepository.save(new GroupMessageCopy(message.getId(), recipient, content)));
        } else {
            if (request.getContent() == null || request.getContent().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
            }
            message = messageRepository.save(GroupMessage.text(access.group().getId(), access.user(),
                    request.getContent(), false, ZonedDateTime.now()));
        }

        // Sending reads everything before it.
        access.member().setLastReadAt(message.getSentAt());
        memberRepository.save(access.member());

        List<User> others = members.stream().map(GroupMember::getUser)
                .filter(u -> !u.getId().equals(access.user().getId())).toList();
        // Never put message text into the push.
        notify(access.user(), access.group(), others, access.user().getUsername() + " sent a message");
        return ResponseEntity.status(HttpStatus.CREATED).body(toMessageResponse(message, access.user(),
                Map.of(), null));
    }

    /** Deletes your own messages, for everyone in the group. */
    @PostMapping("/{groupId}/messages/delete")
    @Transactional
    public ResponseEntity<Void> deleteMessages(
            @PathVariable String groupId, @RequestBody DeleteMessagesRequest request,
            @RequestHeader("Authorization") String authorizationHeader
    ) {
        Access access = access(authorizationHeader, groupId);
        List<UUID> ids = request.getIds() == null ? List.of() : request.getIds().stream().map(this::parseId).toList();
        List<GroupMessage> own = messageRepository.findAllByGroupIdAndSenderAndIdIn(access.group().getId(), access.user(), ids)
                .stream().filter(m -> m.getEvent() == null).toList();
        if (!own.isEmpty()) {
            copyRepository.deleteAllForMessages(own.stream().map(GroupMessage::getId).toList());
            messageRepository.deleteAll(own);
        }
        return ResponseEntity.noContent().build();
    }

    // MARK: helpers

    private User currentUser(String authorizationHeader) {
        User user = jwtTokenUtil.getUserFromToken(userRepository, authorizationHeader);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return user;
    }

    private Access access(String authorizationHeader, String groupId) {
        User user = currentUser(authorizationHeader);
        ChatGroup group = groupRepository.findById(parseId(groupId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        GroupMember member = memberRepository.findByGroupIdAndUser(group.getId(), user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return new Access(user, group, member);
    }

    private void requireAdmin(Access access) {
        if (!access.member().isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    private GroupMember memberOf(ChatGroup group, UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return memberRepository.findByGroupIdAndUser(group.getId(), user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private UUID parseId(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid id.");
        }
    }

    private static String validName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty() || trimmed.length() > ChatGroup.MAX_NAME_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The group needs a name of up to "
                    + ChatGroup.MAX_NAME_LENGTH + " characters.");
        }
        return trimmed;
    }

    private List<User> findUsers(Collection<String> ids) {
        if (ids == null) return List.of();
        return userRepository.findAllById(new LinkedHashSet<>(ids.stream().map(this::parseId).toList()));
    }

    private void notify(User actor, ChatGroup group, List<User> receivers, String body) {
        receivers.forEach(receiver -> notificationService.pushUser(actor, receiver, group.getId(),
                NotificationTypeEnum.NEW_GROUP_MESSAGE, group.getName(), body));
    }

    /** The requester's encrypted copies of these messages, by message id. */
    private Map<UUID, String> copiesFor(User user, List<GroupMessage> messages) {
        List<UUID> secured = messages.stream().filter(m -> Boolean.TRUE.equals(m.getSecured())).map(GroupMessage::getId).toList();
        if (secured.isEmpty()) return Map.of();
        return copyRepository.findAllByRecipientIdAndMessageIdIn(user.getId(), secured).stream()
                .collect(Collectors.toMap(GroupMessageCopy::getMessageId, GroupMessageCopy::getContent, (a, b) -> a));
    }

    /**
     * The other members whose reading counts for "Seen": those with read receipts on. Null when the
     * requester has them off (then they don't see others' either, like direct messages).
     */
    private List<GroupMember> receiptReaders(Access access) {
        if (Boolean.FALSE.equals(access.user().getReadReceipts())) return null;
        return memberRepository.findAllByGroupIdOrderByJoinedAtAsc(access.group().getId()).stream()
                .filter(m -> !m.getId().equals(access.member().getId()))
                .filter(m -> !Boolean.FALSE.equals(m.getUser().getReadReceipts()))
                .toList();
    }

    private GroupMessageResponse toMessageResponse(GroupMessage m, User viewer, Map<UUID, String> copies,
                                                   List<GroupMember> readers) {
        boolean own = m.getSender() != null && m.getSender().getId().equals(viewer.getId());
        Boolean isRead = null;
        // Seen once every other member (sharing receipts) has read the group past this message.
        if (own && m.getEvent() == null && readers != null && !readers.isEmpty()) {
            isRead = readers.stream().allMatch(r -> r.getLastReadAt() != null && !r.getLastReadAt().isBefore(m.getSentAt()));
        }
        boolean secured = Boolean.TRUE.equals(m.getSecured());
        String content = secured ? copies.get(m.getId()) : m.getContent();
        User sender = m.getSender();
        User target = m.getTargetUser();
        return new GroupMessageResponse(
                m.getId().toString(),
                sender == null ? null : sender.getId().toString(),
                sender == null ? null : sender.getUsername(),
                sender == null ? null : sender.getProfileImageUrl(),
                content == null ? "" : content,
                TimeFormat.iso(m.getSentAt()),
                secured, isRead,
                m.getEvent() == null ? null : m.getEvent().name(),
                target == null ? null : target.getId().toString(),
                target == null ? null : target.getUsername());
    }

    private GroupResponse toResponse(ChatGroup group, User viewer) {
        List<GroupMember> members = memberRepository.findAllByGroupIdOrderByJoinedAtAsc(group.getId());
        boolean isAdmin = members.stream().anyMatch(m -> m.getUser().getId().equals(viewer.getId()) && m.isAdmin());
        List<GroupResponse.Member> memberResponses = members.stream()
                .map(m -> new GroupResponse.Member(UserResponse.summary(m.getUser()), m.isAdmin(),
                        m.getUser().getId().equals(group.getCreatedBy())))
                // Owner first, then admins, then everyone else by name.
                .sorted(Comparator.comparing((GroupResponse.Member m) -> !m.getCreator())
                        .thenComparing(m -> !m.getAdmin())
                        .thenComparing(m -> m.getUser().getUsername().toLowerCase()))
                .toList();
        return new GroupResponse(group.getId().toString(), group.getName(), group.getCreatedBy().toString(),
                TimeFormat.iso(group.getCreatedAt()), memberResponses, isAdmin,
                viewer.getId().equals(group.getCreatedBy()));
    }
}
