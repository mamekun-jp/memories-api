package jp.mamekun.memories.api.service;

import jakarta.transaction.Transactional;
import jp.mamekun.memories.api.model.ChatGroup;
import jp.mamekun.memories.api.model.GroupMember;
import jp.mamekun.memories.api.model.GroupMessage;
import jp.mamekun.memories.api.model.User;
import jp.mamekun.memories.api.model.enums.GroupEventEnum;
import jp.mamekun.memories.api.repository.ChatGroupRepository;
import jp.mamekun.memories.api.repository.GroupMemberRepository;
import jp.mamekun.memories.api.repository.GroupMessageCopyRepository;
import jp.mamekun.memories.api.repository.GroupMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/** Membership changes that keep a group consistent: an owner while anyone is left, no empty groups. */
@Service
@RequiredArgsConstructor
public class GroupChatService {
    private final ChatGroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;
    private final GroupMessageRepository messageRepository;
    private final GroupMessageCopyRepository copyRepository;

    /** Adds a notice such as "alice added bob" to the group's timeline. */
    public GroupMessage addEvent(ChatGroup group, User actor, GroupEventEnum event, User target, String content) {
        return messageRepository.save(GroupMessage.event(group.getId(), actor, event, target, content, ZonedDateTime.now()));
    }

    /**
     * Takes someone out of a group (they left, or an admin removed them). When the owner goes, the
     * longest-standing admin, or else the longest-standing member, becomes the owner. A group
     * nobody is left in is deleted. {@code event} may be null to leave no notice.
     */
    @Transactional
    public void removeMember(ChatGroup group, GroupMember member, User actor, GroupEventEnum event) {
        memberRepository.delete(member);
        List<GroupMember> remaining = memberRepository.findAllByGroupIdOrderByJoinedAtAsc(group.getId()).stream()
                .filter(m -> !m.getId().equals(member.getId())).toList();
        if (remaining.isEmpty()) {
            deleteGroup(group);
            return;
        }
        if (member.getUser().getId().equals(group.getCreatedBy())) {
            GroupMember owner = remaining.stream().filter(GroupMember::isAdmin).findFirst().orElse(remaining.get(0));
            owner.setAdmin(true);
            memberRepository.save(owner);
            group.setCreatedBy(owner.getUser().getId());
            groupRepository.save(group);
        }
        if (event != null) {
            addEvent(group, actor, event, member.getUser(), null);
        }
    }

    @Transactional
    public void deleteGroup(ChatGroup group) {
        List<UUID> messageIds = messageRepository.findAllByGroupId(group.getId()).stream().map(GroupMessage::getId).toList();
        if (!messageIds.isEmpty()) {
            copyRepository.deleteAllForMessages(messageIds);
        }
        messageRepository.deleteAllInGroup(group.getId());
        memberRepository.deleteAllByGroupId(group.getId());
        groupRepository.delete(group);
    }

    /** For account deletion: leaves every group and removes everything the account sent or was named in. */
    @Transactional
    public void deleteAllFor(User user) {
        for (GroupMember member : memberRepository.findAllByUser(user)) {
            groupRepository.findById(member.getGroupId())
                    .ifPresentOrElse(group -> removeMember(group, member, user, null), () -> memberRepository.delete(member));
        }
        List<GroupMessage> involving = messageRepository.findAllInvolving(user);
        if (!involving.isEmpty()) {
            copyRepository.deleteAllForMessages(involving.stream().map(GroupMessage::getId).toList());
            messageRepository.deleteAll(involving);
        }
        copyRepository.deleteAllForRecipient(user.getId());
    }
}
