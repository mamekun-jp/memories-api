package jp.mamekun.memories.api.controller;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jp.mamekun.memories.api.model.Block;
import jp.mamekun.memories.api.model.Comment;
import jp.mamekun.memories.api.model.Complaint;
import jp.mamekun.memories.api.model.Like;
import jp.mamekun.memories.api.model.Notification;
import jp.mamekun.memories.api.model.Post;
import jp.mamekun.memories.api.model.User;
import jp.mamekun.memories.api.model.api.CommentRequest;
import jp.mamekun.memories.api.model.api.CommentResponse;
import jp.mamekun.memories.api.model.api.PostDetailsProjection;
import jp.mamekun.memories.api.model.api.PostRequest;
import jp.mamekun.memories.api.model.api.PostResponse;
import jp.mamekun.memories.api.model.api.UserResponse;
import jp.mamekun.memories.api.model.enums.NotificationTypeEnum;
import jp.mamekun.memories.api.model.enums.PostTypeEnum;
import jp.mamekun.memories.api.repository.BlockRepository;
import jp.mamekun.memories.api.repository.CommentRepository;
import jp.mamekun.memories.api.repository.ComplaintRepository;
import jp.mamekun.memories.api.repository.LikeRepository;
import jp.mamekun.memories.api.repository.NotificationRepository;
import jp.mamekun.memories.api.repository.PostRepository;
import jp.mamekun.memories.api.repository.UserRepository;
import jp.mamekun.memories.api.util.JwtTokenUtil;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/posts")
@Log4j2
public class PostsController {
    private final NotificationRepository notificationRepository;
    // Define the directory where files will be saved
    @Value("${image.path:'/data/memories-img'}")
    private String uploadDir;

    // Define the directory where files will be saved
    @Value("${image.thumbnail-path:'/data/memories-img/th'}")
    private String thUploadDir;

    @Value("${video.path:'/data/memories-vid'}")
    private String vidUploadDir;

    @Value("${video.thumbnail-path:'/data/memories-vid/th'}")
    private String vidThUploadDir;

    @Value("${app.broadcast-posts:false}")
    private boolean broadcastPosts;

    private final JwtTokenUtil jwtTokenUtil;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final LikeRepository likeRepository;
    private final ComplaintRepository complaintRepository;
    private final BlockRepository blockRepository;
    private final CommentRepository commentRepository;

    public PostsController(
            JwtTokenUtil jwtTokenUtil, PostRepository postRepository, UserRepository userRepository,
            LikeRepository likeRepository, ComplaintRepository complaintRepository, BlockRepository blockRepository,
            CommentRepository commentRepository, NotificationRepository notificationRepository
    ) {
        this.jwtTokenUtil = jwtTokenUtil;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.likeRepository = likeRepository;
        this.complaintRepository = complaintRepository;
        this.blockRepository = blockRepository;
        this.commentRepository = commentRepository;
        this.notificationRepository = notificationRepository;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<PostResponse> createPost(
            @RequestBody @Valid PostRequest postRequest, @RequestHeader("Authorization") String authorizationHeader
    ) {
        User user = jwtTokenUtil.getUserFromToken(userRepository, authorizationHeader);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Post newPost = new Post();
        newPost.setIsDeleted(false);
        newPost.setOwner(user);
        newPost.setCaption(postRequest.getCaption());
        newPost.setLikes(0);
        newPost.setImageUrl(postRequest.getImageUrl());
        newPost.setTimestamp(ZonedDateTime.now());
        if (postRequest.getPostType() != null) {
            newPost.setPostType(PostTypeEnum.valueOf(postRequest.getPostType()));
        } else {
            newPost.setPostType(PostTypeEnum.IMAGE);
        }

        Post savedPost = postRepository.save(newPost);

        if (broadcastPosts) {
            Notification notification = new Notification();
            notification.setTargetId(savedPost.getId());
            notification.setContent(NotificationTypeEnum.NEW_POST.name());
            notification.setIsPublic(true);
            notification.setTimestamp(ZonedDateTime.now());
            notification.setOwner(user);
            notificationRepository.save(notification);
        }

        return ResponseEntity.ok(new PostResponse(
                savedPost.getId().toString(), user.getId().toString(), savedPost.getCaption(),
                savedPost.getLikes(), 0, savedPost.getImageUrl(), savedPost.getPostType().toString(),
                savedPost.getTimestamp().toString(), user.getProfileImageUrl(), user.getUsername()
        ));
    }

    @GetMapping("/feed")
    public ResponseEntity<List<PostResponse>> getFeed(
            @RequestHeader("Authorization") String authorizationHeader
    ) {
        User user = jwtTokenUtil.getUserFromToken(userRepository, authorizationHeader);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Fetch blocked user IDs
        List<UUID> blockedUsers = blockRepository.findAllByUserId(user.getId()).stream()
                .map(Block::getBlockedUserId).toList();

        // Fetch and process posts
        List<PostResponse> posts = postRepository.findPostsWithDetailsByIsDeleted(false)
                .stream()
                .filter(post -> isNotBlocked(UUID.fromString(post.getPostId()), blockedUsers))
                .map(this::mapToPostResponse)
                .toList();

        return ResponseEntity.ok(posts);
    }

    @GetMapping("/feed/{postId}")
    public ResponseEntity<PostResponse> getFeedByPostId(
            @PathVariable("postId") String postId, @RequestHeader("Authorization") String authorizationHeader
    ) {
        User user = jwtTokenUtil.getUserFromToken(userRepository, authorizationHeader);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Fetch blocked user IDs
        List<UUID> blockedUsers = blockRepository.findAllByUserId(user.getId()).stream()
                .map(Block::getBlockedUserId).toList();

        // Fetch and process posts
        PostResponse response = postRepository.findPostByIdAndIsDeleted(UUID.fromString(postId), false)
                .stream()
                .findFirst()
                .filter(post -> isNotBlocked(post.getOwner().getId(), blockedUsers))
                .map(post -> new PostResponse(post.getId().toString(),
                        post.getOwner().getId().toString(), post.getCaption(),
                        likeRepository.countLikesByPostId(post.getId()),
                        commentRepository.countCommentsByPostId(post.getId()),
                        post.getImageUrl(), post.getPostType().toString(),
                        post.getTimestamp().toString(), post.getOwner().getProfileImageUrl(),
                        post.getOwner().getUsername())).orElse(null);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<PostResponse>> getUserFeed(
            @PathVariable("userId") String userId, @RequestHeader("Authorization") String authorizationHeader
    ) {
        User user = new User();
        user.setId(UUID.fromString(userId));
        List<PostResponse> posts = postRepository.findAllByOwnerAndIsDeletedOrderByTimestampDesc(user, false)
                .stream().map(post -> new PostResponse(post.getId().toString(),
                        post.getOwner().getId().toString(), post.getCaption(),
                        likeRepository.countLikesByPostId(post.getId()),
                        commentRepository.countCommentsByPostId(post.getId()),
                        post.getImageUrl(), post.getPostType().toString(),
                        post.getTimestamp().toString(), post.getOwner().getProfileImageUrl(),
                        post.getOwner().getUsername())).toList();
        return ResponseEntity.ok(posts);
    }

    @GetMapping("/like/{postId}")
    @Transactional
    public ResponseEntity<String> likePost(
            @PathVariable("postId") String postId, @RequestHeader("Authorization") String authorizationHeader
    ) {
        User user = jwtTokenUtil.getUserFromToken(userRepository, authorizationHeader);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        postRepository.findById(UUID.fromString(postId))
            .ifPresent(post -> likeRepository.findByPostIdAndUser(post.getId(), user)
                .ifPresentOrElse(
                    like -> {
                        // Like exists, you can add logic here if needed
                    },
                    () -> likeRepository.save(new Like(user, post.getId(), true))
                ));
        return ResponseEntity.ok("OK");
    }

    @GetMapping("/unlike/{postId}")
    @Transactional
    public ResponseEntity<String> unlikePost(
            @PathVariable("postId") String postId, @RequestHeader("Authorization") String authorizationHeader
    ) {
        User user = jwtTokenUtil.getUserFromToken(userRepository, authorizationHeader);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        postRepository.findById(UUID.fromString(postId)).flatMap(post ->
                likeRepository.findByPostIdAndUser(post.getId(), user)
        ).ifPresent(like -> likeRepository.deleteById(like.getId()));
        return ResponseEntity.ok("OK");
    }

    @GetMapping("/report/{postId}")
    @Transactional
    public ResponseEntity<String> reportPost(
            @PathVariable("postId") String postId, @RequestHeader("Authorization") String authorizationHeader
    ) {
        User user = jwtTokenUtil.getUserFromToken(userRepository, authorizationHeader);
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
            @PathVariable("postId") String postId, @RequestHeader("Authorization") String authorizationHeader
    ) {
        User user = jwtTokenUtil.getUserFromToken(userRepository, authorizationHeader);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        postRepository.findById(UUID.fromString(postId)).ifPresent(post ->
                {
                    if (post.getOwner().getId().equals(user.getId())) {
                        String filePath;
                        String thumbnailPath;
                        String filename = post.getImageUrl().substring(5);
                        postRepository.delete(post);
                        if (post.getPostType() != null && post.getPostType().equals(PostTypeEnum.VIDEO)) {
                            filePath = vidUploadDir + "/" + filename;
                            thumbnailPath = vidThUploadDir + "/" + filename.replace(".mp4", ".jpg");
                        } else {
                            filePath = uploadDir + "/" + filename;
                            thumbnailPath = thUploadDir + "/" + filename;
                        }
                        try {
                            Files.delete(Paths.get(filePath));
                        } catch (IOException e) {
                            log.error("Failed to delete file", e);
                        }
                        try {
                            Files.delete(Paths.get(thumbnailPath));
                        } catch (IOException e) {
                            log.error("Failed to delete thumbnail file", e);
                        }
                    }
                }
        );
        return ResponseEntity.ok("OK");
    }

    @GetMapping("/comments/{postId}")
    @Transactional
    public ResponseEntity<List<CommentResponse>> getComments(@PathVariable("postId") String postId) {
        List<CommentResponse> response = commentRepository.findByPostId(UUID.fromString(postId))
                .stream().map(comment -> new CommentResponse(
                        comment.getId().toString(), comment.getPostId().toString(),
                        comment.getOwner().getId().toString(), comment.getContent(),
                        comment.getTimestamp().toString(), comment.getOwner().getProfileImageUrl(),
                        comment.getOwner().getUsername())
                ).toList();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/comments/{postId}")
    @Transactional
    public ResponseEntity<String> addComment(
            @RequestBody @Valid CommentRequest commentRequest, @PathVariable("postId") String postId,
            @RequestHeader("Authorization") String authorizationHeader
    ) {
        User user = jwtTokenUtil.getUserFromToken(userRepository, authorizationHeader);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Comment comment = new Comment();
        comment.setPostId(UUID.fromString(postId));
        comment.setIsDeleted(false);
        comment.setContent(commentRequest.getContent());
        comment.setTimestamp(ZonedDateTime.now());
        comment.setOwner(user);
        commentRepository.save(comment);

        postRepository.findById(UUID.fromString(postId)).ifPresent(post ->
                {
                    commentRepository.findOwnersByPostId(post.getId(), user.getId()).forEach(ownerId -> {
                        Notification notification = new Notification();
                        notification.setTargetId(post.getId());
                        notification.setContent(NotificationTypeEnum.NEW_COMMENT.name());
                        notification.setReceiver(ownerId);
                        notification.setIsPublic(false);
                        notification.setTimestamp(ZonedDateTime.now());
                        notification.setOwner(user);
                        notificationRepository.save(notification);
                    });
                    Notification notification = new Notification();
                    notification.setTargetId(post.getId());
                    notification.setContent(NotificationTypeEnum.NEW_COMMENT.name());
                    notification.setReceiver(post.getOwner().getId());
                    notification.setIsPublic(false);
                    notification.setTimestamp(ZonedDateTime.now());
                    notification.setOwner(user);
                    notificationRepository.save(notification);
                }
        );

        return ResponseEntity.ok("OK");
    }

    @DeleteMapping("/comments/{postId}/{commentId}")
    @Transactional
    public ResponseEntity<String> deleteComment(
            @PathVariable("postId") String postId, @PathVariable("commentId") String commentId,
            @RequestHeader("Authorization") String authorizationHeader
    ) {
        User user = jwtTokenUtil.getUserFromToken(userRepository, authorizationHeader);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        commentRepository.findById(UUID.fromString(commentId)).ifPresent(comment -> {
            if (comment.getOwner().getId().equals(user.getId())) {
                commentRepository.delete(comment);
            }
        });

        return ResponseEntity.ok("OK");
    }

    private boolean isNotBlocked(UUID uuid, List<UUID> blockedUsers) {
        return !blockedUsers.contains(uuid);
    }

    private PostResponse mapToPostResponse(PostDetailsProjection post) {
        return new PostResponse(
                post.getPostId(),
                post.getOwnerId(),
                post.getCaption(),
                post.getLikeCount(),
                post.getCommentCount(),
                post.getImageUrl(),
                post.getPostType(),
                post.getTimestamp(),
                post.getProfileImageUrl(),
                post.getUsername()
        );
    }

    @GetMapping("/likes/{postId}")
    public ResponseEntity<List<UserResponse>> getLikesByPostId(
            @PathVariable("postId") String postId, @RequestHeader("Authorization") String authorizationHeader
    ) {
        User user = jwtTokenUtil.getUserFromToken(userRepository, authorizationHeader);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Fetch and process posts
        List<UserResponse> response = likeRepository.findByPostId(UUID.fromString(postId)).stream().map(like -> {
            User sender = like.getUser();
            return new UserResponse(
                    sender.getId().toString(), sender.getUsername(), sender.getProfileImageUrl(), sender.getFullName(),
                    sender.getBio(), "", false, 0, 0, 0);
        }).toList();

        return ResponseEntity.ok(response);
    }
}
