package jp.mamekun.memories.api.controller;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jp.mamekun.memories.api.model.Comment;
import jp.mamekun.memories.api.model.CommentLike;
import jp.mamekun.memories.api.model.Complaint;
import jp.mamekun.memories.api.model.Like;
import jp.mamekun.memories.api.model.Post;
import jp.mamekun.memories.api.model.SavedPost;
import jp.mamekun.memories.api.model.StoryView;
import jp.mamekun.memories.api.model.User;
import jp.mamekun.memories.api.model.api.CommentRequest;
import jp.mamekun.memories.api.model.api.CommentResponse;
import jp.mamekun.memories.api.model.api.PostRequest;
import jp.mamekun.memories.api.model.api.PostResponse;
import jp.mamekun.memories.api.model.api.PostUpdateRequest;
import jp.mamekun.memories.api.model.api.UserResponse;
import jp.mamekun.memories.api.model.enums.NotificationTypeEnum;
import jp.mamekun.memories.api.model.enums.PostTypeEnum;
import jp.mamekun.memories.api.repository.CommentLikeRepository;
import jp.mamekun.memories.api.repository.CommentRepository;
import jp.mamekun.memories.api.repository.ComplaintRepository;
import jp.mamekun.memories.api.repository.LikeRepository;
import jp.mamekun.memories.api.repository.PostRepository;
import jp.mamekun.memories.api.repository.SavedPostRepository;
import jp.mamekun.memories.api.repository.StoryViewRepository;
import jp.mamekun.memories.api.repository.UserRepository;
import jp.mamekun.memories.api.service.AccountService;
import jp.mamekun.memories.api.service.NotificationService;
import jp.mamekun.memories.api.util.JwtTokenUtil;
import jp.mamekun.memories.api.util.TimeFormat;
import lombok.extern.log4j.Log4j2;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZonedDateTime;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/posts")
@Log4j2
public class PostsController {
    /** Stories disappear from feeds this many hours after they were posted. */
    @Value("${app.story-lifetime-hours:24}")
    private long storyLifetimeHours;

    @Value("${app.broadcast-posts:false}")
    private boolean broadcastPosts;

    private static final int MAX_PAGE_SIZE = 50;

    private final JwtTokenUtil jwtTokenUtil;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final LikeRepository likeRepository;
    private final ComplaintRepository complaintRepository;
    private final CommentRepository commentRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final SavedPostRepository savedPostRepository;
    private final StoryViewRepository storyViewRepository;
    private final NotificationService notificationService;
    private final AccountService accountService;

    public PostsController(
            JwtTokenUtil jwtTokenUtil, PostRepository postRepository, UserRepository userRepository,
            LikeRepository likeRepository, ComplaintRepository complaintRepository,
            CommentRepository commentRepository, CommentLikeRepository commentLikeRepository,
            SavedPostRepository savedPostRepository, StoryViewRepository storyViewRepository,
            NotificationService notificationService, AccountService accountService) {
        this.jwtTokenUtil = jwtTokenUtil;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.likeRepository = likeRepository;
        this.complaintRepository = complaintRepository;
        this.commentRepository = commentRepository;
        this.commentLikeRepository = commentLikeRepository;
        this.savedPostRepository = savedPostRepository;
        this.storyViewRepository = storyViewRepository;
        this.notificationService = notificationService;
        this.accountService = accountService;
    }

    private ZonedDateTime storyCutoff() {
        return ZonedDateTime.now().minusHours(storyLifetimeHours);
    }

    private User currentUser(String authorizationHeader) {
        return jwtTokenUtil.getUserFromToken(userRepository, authorizationHeader);
    }

    @PostMapping
    @Transactional
    public ResponseEntity<PostResponse> createPost(
            @RequestBody @Valid PostRequest postRequest, @RequestHeader("Authorization") String authorizationHeader
    ) {
        User user = currentUser(authorizationHeader);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        PostTypeEnum postType;
        try {
            postType = postRequest.getPostType() != null
                    ? PostTypeEnum.valueOf(postRequest.getPostType()) : PostTypeEnum.IMAGE;
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }

        Post newPost = new Post();
        newPost.setIsDeleted(false);
        newPost.setOwner(user);
        newPost.setCaption(postRequest.getCaption());
        newPost.setLikes(0);
        newPost.setImageUrl(postRequest.getImageUrl());
        newPost.setTimestamp(ZonedDateTime.now());
        newPost.setPostType(postType);

        Post savedPost = postRepository.save(newPost);

        if (broadcastPosts) {
            boolean isStory = postType == PostTypeEnum.STORY_IMAGE || postType == PostTypeEnum.STORY_VIDEO;
            notificationService.notifyEveryone(
                    user, savedPost.getId(),
                    isStory ? NotificationTypeEnum.NEW_STORY : NotificationTypeEnum.NEW_POST,
                    user.getUsername(),
                    isStory ? "added a new story" : "shared a new post"
            );
        }

        return ResponseEntity.ok(new PostResponse(
                savedPost.getId().toString(), user.getId().toString(), savedPost.getCaption(),
                0, 0, savedPost.getImageUrl(), savedPost.getPostType().toString(),
                TimeFormat.iso(savedPost.getTimestamp()), user.getProfileImageUrl(), user.getUsername(),
                false, false, false
        ));
    }

    /**
     * Without {@code page}: every post plus active stories (what older app versions expect).
     * With {@code page}: one page of regular posts only; fetch stories from {@code /stories}.
     */
    @GetMapping("/feed")
    public ResponseEntity<List<PostResponse>> getFeed(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size,
            @RequestHeader("Authorization") String authorizationHeader
    ) {
        User user = currentUser(authorizationHeader);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (page == null) {
            return ResponseEntity.ok(postRepository.findFeed(user.getId(), storyCutoff())
                    .stream().map(PostResponse::from).toList());
        }

        int pageSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        int offset = Math.max(0, page) * pageSize;
        return ResponseEntity.ok(postRepository.findFeedPage(user.getId(), pageSize, offset)
                .stream().map(PostResponse::from).toList());
    }

    @GetMapping("/stories")
    public ResponseEntity<List<PostResponse>> getStories(@RequestHeader("Authorization") String authorizationHeader) {
        User user = currentUser(authorizationHeader);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(postRepository.findActiveStories(user.getId(), storyCutoff())
                .stream().map(PostResponse::from).toList());
    }

    @GetMapping("/feed/{postId}")
    public ResponseEntity<PostResponse> getFeedByPostId(
            @PathVariable String postId, @RequestHeader("Authorization") String authorizationHeader
    ) {
        User user = currentUser(authorizationHeader);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return postRepository.findDetailsById(user.getId(), UUID.fromString(postId))
                .map(PostResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<PostResponse>> getUserFeed(
            @PathVariable String userId, @RequestHeader("Authorization") String authorizationHeader
    ) {
        User user = currentUser(authorizationHeader);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(postRepository.findByOwner(user.getId(), UUID.fromString(userId), storyCutoff())
                .stream().map(PostResponse::from).toList());
    }

    @PutMapping("/{postId}")
    @Transactional
    public ResponseEntity<PostResponse> updatePost(
            @PathVariable String postId, @RequestBody PostUpdateRequest request,
            @RequestHeader("Authorization") String authorizationHeader
    ) {
        User user = currentUser(authorizationHeader);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Optional<Post> postOpt = postRepository.findById(UUID.fromString(postId));
        if (postOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Post post = postOpt.get();
        if (!post.getOwner().getId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (request.getCaption() != null) {
            post.setCaption(request.getCaption());
            postRepository.save(post);
        }

        return postRepository.findDetailsById(user.getId(), post.getId())
                .map(PostResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/like/{postId}")
    @Transactional
    public ResponseEntity<String> likePost(
            @PathVariable String postId, @RequestHeader("Authorization") String authorizationHeader
    ) {
        User user = currentUser(authorizationHeader);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        postRepository.findById(UUID.fromString(postId)).ifPresent(post -> {
            if (likeRepository.findByPostIdAndUser(post.getId(), user).isEmpty()) {
                likeRepository.save(new Like(user, post.getId(), true));
                notificationService.notifyUser(user, post.getOwner().getId(), post.getId(),
                        NotificationTypeEnum.NEW_LIKE, "New like", user.getUsername() + " liked your post");
            }
        });
        return ResponseEntity.ok("OK");
    }

    @GetMapping("/unlike/{postId}")
    @Transactional
    public ResponseEntity<String> unlikePost(
            @PathVariable String postId, @RequestHeader("Authorization") String authorizationHeader
    ) {
        User user = currentUser(authorizationHeader);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        likeRepository.findByPostIdAndUser(UUID.fromString(postId), user)
                .ifPresent(like -> likeRepository.deleteById(like.getId()));
        return ResponseEntity.ok("OK");
    }

    @PostMapping("/{postId}/save")
    @Transactional
    public ResponseEntity<String> savePost(
            @PathVariable String postId, @RequestHeader("Authorization") String authorizationHeader
    ) {
        User user = currentUser(authorizationHeader);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        UUID id = UUID.fromString(postId);
        if (postRepository.existsById(id) && !savedPostRepository.existsByUserIdAndPostId(user.getId(), id)) {
            savedPostRepository.save(new SavedPost(user.getId(), id, ZonedDateTime.now()));
        }
        return ResponseEntity.ok("OK");
    }

    @DeleteMapping("/{postId}/save")
    @Transactional
    public ResponseEntity<String> unsavePost(
            @PathVariable String postId, @RequestHeader("Authorization") String authorizationHeader
    ) {
        User user = currentUser(authorizationHeader);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        savedPostRepository.deleteAllByUserIdAndPostId(user.getId(), UUID.fromString(postId));
        return ResponseEntity.ok("OK");
    }

    @GetMapping("/saved")
    public ResponseEntity<List<PostResponse>> getSavedPosts(@RequestHeader("Authorization") String authorizationHeader) {
        User user = currentUser(authorizationHeader);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(postRepository.findSaved(user.getId()).stream().map(PostResponse::from).toList());
    }

    /** Called by the app when someone watches a story. Owners watching their own story are not counted. */
    @PostMapping("/{postId}/view")
    @Transactional
    public ResponseEntity<String> markStoryViewed(
            @PathVariable String postId, @RequestHeader("Authorization") String authorizationHeader
    ) {
        User user = currentUser(authorizationHeader);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        postRepository.findById(UUID.fromString(postId)).ifPresent(post -> {
            if (!post.getOwner().getId().equals(user.getId())
                    && !storyViewRepository.existsByPostIdAndViewer(post.getId(), user)) {
                storyViewRepository.save(new StoryView(post.getId(), user, ZonedDateTime.now()));
            }
        });
        return ResponseEntity.ok("OK");
    }

    /** Who watched a story. Only the story owner may ask. */
    @GetMapping("/{postId}/views")
    public ResponseEntity<List<UserResponse>> getStoryViewers(
            @PathVariable String postId, @RequestHeader("Authorization") String authorizationHeader
    ) {
        User user = currentUser(authorizationHeader);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Optional<Post> postOpt = postRepository.findById(UUID.fromString(postId));
        if (postOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (!postOpt.get().getOwner().getId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(storyViewRepository.findAllByPostIdOrderByTimestampDesc(postOpt.get().getId())
                .stream().map(view -> UserResponse.summary(view.getViewer())).toList());
    }

    /** The caller's own stories, expired ones included, so they can pick highlights. */
    @GetMapping("/stories/archive")
    public ResponseEntity<List<PostResponse>> getStoryArchive(@RequestHeader("Authorization") String authorizationHeader) {
        User user = currentUser(authorizationHeader);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(postRepository.findStoryArchive(user.getId())
                .stream().map(PostResponse::from).toList());
    }

    /** Stories a user pinned to their profile. */
    @GetMapping("/user/{userId}/highlights")
    public ResponseEntity<List<PostResponse>> getHighlights(
            @PathVariable String userId, @RequestHeader("Authorization") String authorizationHeader
    ) {
        User user = currentUser(authorizationHeader);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(postRepository.findHighlights(user.getId(), UUID.fromString(userId))
                .stream().map(PostResponse::from).toList());
    }

    @PostMapping("/{postId}/highlight")
    @Transactional
    public ResponseEntity<String> addHighlight(
            @PathVariable String postId, @RequestHeader("Authorization") String authorizationHeader
    ) {
        return setHighlighted(postId, true, authorizationHeader);
    }

    @DeleteMapping("/{postId}/highlight")
    @Transactional
    public ResponseEntity<String> removeHighlight(
            @PathVariable String postId, @RequestHeader("Authorization") String authorizationHeader
    ) {
        return setHighlighted(postId, false, authorizationHeader);
    }

    private ResponseEntity<String> setHighlighted(String postId, boolean highlighted, String authorizationHeader) {
        User user = currentUser(authorizationHeader);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Optional<Post> postOpt = postRepository.findPostByIdAndIsDeleted(UUID.fromString(postId), false);
        if (postOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Post post = postOpt.get();
        if (!post.getOwner().getId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (post.getPostType() != PostTypeEnum.STORY_IMAGE && post.getPostType() != PostTypeEnum.STORY_VIDEO) {
            return ResponseEntity.badRequest().build();
        }
        post.setHighlighted(highlighted);
        postRepository.save(post);
        return ResponseEntity.ok("OK");
    }

    @GetMapping("/report/{postId}")
    @Transactional
    public ResponseEntity<String> reportPost(
            @PathVariable String postId, @RequestHeader("Authorization") String authorizationHeader
    ) {
        User user = currentUser(authorizationHeader);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        postRepository.findById(UUID.fromString(postId)).ifPresent(post ->
            complaintRepository.save(new Complaint(user.getId(), post.getId(), ZonedDateTime.now()))
        );
        return ResponseEntity.ok("OK");
    }

    @DeleteMapping("/delete/{postId}")
    @Transactional
    public ResponseEntity<String> deletePost(
            @PathVariable String postId, @RequestHeader("Authorization") String authorizationHeader
    ) {
        User user = currentUser(authorizationHeader);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Optional<Post> postOpt = postRepository.findById(UUID.fromString(postId));
        if (postOpt.isPresent()) {
            if (!postOpt.get().getOwner().getId().equals(user.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            accountService.deletePost(postOpt.get());
        }
        return ResponseEntity.ok("OK");
    }

    @GetMapping("/comments/{postId}")
    @Transactional
    public ResponseEntity<List<CommentResponse>> getComments(
            @PathVariable String postId,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        List<Comment> comments = commentRepository.findByPostIdOrderByTimestampAsc(UUID.fromString(postId));
        if (comments.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        List<UUID> commentIds = comments.stream().map(Comment::getId).toList();

        Map<UUID, Integer> likeCounts = new HashMap<>();
        commentLikeRepository.countByCommentIds(commentIds)
                .forEach(row -> likeCounts.put((UUID) row[0], ((Number) row[1]).intValue()));

        User viewer = authorizationHeader != null ? currentUser(authorizationHeader) : null;
        Set<UUID> likedByViewer = viewer != null
                ? new HashSet<>(commentLikeRepository.findLikedCommentIds(commentIds, viewer.getId()))
                : Set.of();

        return ResponseEntity.ok(comments.stream()
                .map(c -> toCommentResponse(c, likeCounts.getOrDefault(c.getId(), 0), likedByViewer.contains(c.getId())))
                .toList());
    }

    @PostMapping("/comments/{postId}")
    @Transactional
    public ResponseEntity<CommentResponse> addComment(
            @RequestBody @Valid CommentRequest commentRequest, @PathVariable String postId,
            @RequestHeader("Authorization") String authorizationHeader
    ) {
        User user = currentUser(authorizationHeader);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Optional<Post> postOpt = postRepository.findById(UUID.fromString(postId));
        if (postOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Post post = postOpt.get();

        Comment comment = new Comment();
        comment.setPostId(post.getId());
        comment.setIsDeleted(false);
        comment.setContent(commentRequest.getContent());
        comment.setTimestamp(ZonedDateTime.now());
        comment.setOwner(user);
        Comment saved = commentRepository.save(comment);

        // The post owner and everyone else who commented hear about it, each once.
        Set<UUID> receivers = new LinkedHashSet<>();
        receivers.add(post.getOwner().getId());
        receivers.addAll(commentRepository.findOwnersByPostId(post.getId(), user.getId()));
        receivers.forEach(receiverId -> notificationService.notifyUser(
                user, receiverId, post.getId(), NotificationTypeEnum.NEW_COMMENT,
                "New comment", user.getUsername() + ": " + commentRequest.getContent()));

        return ResponseEntity.ok(toCommentResponse(saved, 0, false));
    }

    @DeleteMapping("/comments/{postId}/{commentId}")
    @Transactional
    public ResponseEntity<String> deleteComment(
            @PathVariable String postId, @PathVariable String commentId,
            @RequestHeader("Authorization") String authorizationHeader
    ) {
        User user = currentUser(authorizationHeader);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Comment authors can delete their comments; post owners can delete any comment on their post.
        commentRepository.findById(UUID.fromString(commentId)).ifPresent(comment -> {
            boolean isAuthor = comment.getOwner().getId().equals(user.getId());
            boolean isPostOwner = postRepository.findById(comment.getPostId())
                    .map(post -> post.getOwner().getId().equals(user.getId()))
                    .orElse(false);
            if (isAuthor || isPostOwner) {
                commentLikeRepository.deleteAllByCommentId(comment.getId());
                commentRepository.delete(comment);
            }
        });

        return ResponseEntity.ok("OK");
    }

    @PostMapping("/comments/{commentId}/like")
    @Transactional
    public ResponseEntity<String> likeComment(
            @PathVariable String commentId, @RequestHeader("Authorization") String authorizationHeader
    ) {
        User user = currentUser(authorizationHeader);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Optional<Comment> commentOpt = commentRepository.findById(UUID.fromString(commentId));
        if (commentOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Comment comment = commentOpt.get();
        if (!commentLikeRepository.existsByCommentIdAndUser(comment.getId(), user)) {
            commentLikeRepository.save(new CommentLike(comment.getId(), user, ZonedDateTime.now()));
            // targetId is the post so tapping the notification opens it.
            notificationService.notifyUser(user, comment.getOwner().getId(), comment.getPostId(),
                    NotificationTypeEnum.NEW_COMMENT_LIKE, "New like",
                    user.getUsername() + " liked your comment: " + comment.getContent());
        }
        return ResponseEntity.ok("OK");
    }

    @DeleteMapping("/comments/{commentId}/like")
    @Transactional
    public ResponseEntity<String> unlikeComment(
            @PathVariable String commentId, @RequestHeader("Authorization") String authorizationHeader
    ) {
        User user = currentUser(authorizationHeader);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        commentLikeRepository.deleteAllByCommentIdAndUser(UUID.fromString(commentId), user);
        return ResponseEntity.ok("OK");
    }

    @GetMapping("/likes/{postId}")
    public ResponseEntity<List<UserResponse>> getLikesByPostId(
            @PathVariable String postId, @RequestHeader("Authorization") String authorizationHeader
    ) {
        User user = currentUser(authorizationHeader);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<UserResponse> response = likeRepository.findByPostId(UUID.fromString(postId)).stream()
                .map(like -> UserResponse.summary(like.getUser()))
                .toList();

        return ResponseEntity.ok(response);
    }

    private static CommentResponse toCommentResponse(Comment comment, int likes, boolean isLiked) {
        return new CommentResponse(
                comment.getId().toString(), comment.getPostId().toString(),
                comment.getOwner().getId().toString(), comment.getContent(),
                TimeFormat.iso(comment.getTimestamp()), comment.getOwner().getProfileImageUrl(),
                comment.getOwner().getUsername(), likes, isLiked);
    }
}
