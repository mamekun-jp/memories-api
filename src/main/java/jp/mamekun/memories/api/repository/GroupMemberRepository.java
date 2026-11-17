package jp.mamekun.memories.api.repository;

import jp.mamekun.memories.api.model.GroupMember;
import jp.mamekun.memories.api.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupMemberRepository extends JpaRepository<GroupMember, UUID> {
    /** Oldest member first. */
    List<GroupMember> findAllByGroupIdOrderByJoinedAtAsc(UUID groupId);

    Optional<GroupMember> findByGroupIdAndUser(UUID groupId, User user);

    List<GroupMember> findAllByUser(User user);

    void deleteAllByGroupId(UUID groupId);
}
