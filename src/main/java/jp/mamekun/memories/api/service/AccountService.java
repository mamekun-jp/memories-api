package jp.mamekun.memories.api.service;

import jakarta.transaction.Transactional;
import jp.mamekun.memories.api.model.Post;
import jp.mamekun.memories.api.model.User;
import jp.mamekun.memories.api.repository.BlockRepository;
import jp.mamekun.memories.api.repository.CommentRepository;
import jp.mamekun.memories.api.repository.ComplaintRepository;
import jp.mamekun.memories.api.repository.FollowRepository;
import jp.mamekun.memories.api.repository.LikeRepository;
import jp.mamekun.memories.api.repository.MessageRepository;
import jp.mamekun.memories.api.repository.NotificationRepository;
import jp.mamekun.memories.api.repository.PasswordResetTokenRepository;
import jp.mamekun.memories.api.repository.PostRepository;
import jp.mamekun.memories.api.repository.SavedPostRepository;
import jp.mamekun.memories.api.repository.StoryViewRepository;
import jp.mamekun.memories.api.repository.UserKeyRepository;
import jp.mamekun.memories.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** Deletes posts and accounts together with everything that references them. */
@Service
@RequiredArgsConstructor
public class AccountService {
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final LikeRepository likeRepository;
    private final CommentRepository commentRepository;
    private final SavedPostRepository savedPostRepository;
    private final StoryViewRepository storyViewRepository;
    private final FollowRepository followRepository;
    private final BlockRepository blockRepository;
    private final MessageRepository messageRepository;
    private final NotificationRepository notificationRepository;
    private final ComplaintRepository complaintRepository;
    private final UserKeyRepository userKeyRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final MediaStorageService mediaStorageService;

    @Transactional
    public void deletePost(Post post) {
        likeRepository.deleteAllByPostId(post.getId());
        commentRepository.deleteAllByPostId(post.getId());
        savedPostRepository.deleteAllByPostId(post.getId());
        storyViewRepository.deleteAllByPostId(post.getId());
        notificationRepository.deleteAllByTargetId(post.getId());
        postRepository.delete(post);
        mediaStorageService.deleteMedia(post.getImageUrl());
    }

    @Transactional
    public void deleteAccount(User user) {
        List<Post> posts = postRepository.findAllByOwner(user);
        posts.forEach(this::deletePost);

        likeRepository.deleteAllByUser(user);
        commentRepository.deleteAllByOwner(user);
        savedPostRepository.deleteAllByUserId(user.getId());
        storyViewRepository.deleteAllByViewer(user);
        followRepository.deleteAllByFollowerId(user.getId());
        followRepository.deleteAllByFolloweeId(user.getId());
        blockRepository.deleteAllByUserId(user.getId());
        blockRepository.deleteAllByBlockedUserId(user.getId());
        messageRepository.deleteAllBySender(user);
        messageRepository.deleteAllByReceiver(user);
        notificationRepository.deleteAllInvolving(user, user.getId());
        complaintRepository.deleteAllByUserId(user.getId());
        userKeyRepository.deleteAllByUser(user);
        passwordResetTokenRepository.deleteByUserId(user.getId());

        if (user.getProfileImageUrl() != null) {
            mediaStorageService.deleteMedia(user.getProfileImageUrl());
        }
        userRepository.delete(user);
    }
}
