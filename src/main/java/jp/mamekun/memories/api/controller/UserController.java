package jp.mamekun.memories.api.controller;

import jakarta.transaction.Transactional;
import jp.mamekun.memories.api.model.Block;
import jp.mamekun.memories.api.model.Follow;
import jp.mamekun.memories.api.model.User;
import jp.mamekun.memories.api.model.api.UserRequest;
import jp.mamekun.memories.api.model.api.UserResponse;
import jp.mamekun.memories.api.repository.BlockRepository;
import jp.mamekun.memories.api.repository.FollowRepository;
import jp.mamekun.memories.api.repository.PostRepository;
import jp.mamekun.memories.api.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static jp.mamekun.memories.api.util.JwtTokenUtil.getUserFromToken;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final BlockRepository blockRepository;
    private final PostRepository postRepository;

    public UserController(
            UserRepository userRepository, FollowRepository followRepository, BlockRepository blockRepository,
            PostRepository postRepository) {
        this.userRepository = userRepository;
        this.followRepository = followRepository;
        this.blockRepository = blockRepository;
        this.postRepository = postRepository;
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> getUsers(@RequestHeader("Authorization") String authorizationHeader) {
        User currentUser = getUserFromToken(userRepository, authorizationHeader);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<UserResponse> response = userRepository.findAllByIsDeletedOrderByUsername(false).stream().map(user ->
                new UserResponse(user.getId().toString(), user.getUsername(), user.getProfileImageUrl(),
                        user.getFullName(), user.getBio(), user.getEmail(),
                        followRepository.existsByFollowerIdAndFolloweeId(currentUser.getId(), user.getId()), 0, 0, 0)).toList();
        return ResponseEntity.ok().body(response);
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getActiveUser(@RequestHeader("Authorization") String authorizationHeader) {
        User user = getUserFromToken(userRepository, authorizationHeader);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UserResponse response = new UserResponse(user.getId().toString(), user.getUsername(),
                user.getProfileImageUrl(), user.getFullName(), user.getBio(), user.getEmail(), false,
                followRepository.countByFolloweeId(user.getId()), followRepository.countByFollowerId(user.getId()),
                postRepository.countByOwner_Id(user.getId()));
        return ResponseEntity.ok().body(response);
    }

    @PutMapping("/me")
    @Transactional
    public ResponseEntity<UserResponse> updateActiveUser(
            @RequestBody UserRequest userRequest, @RequestHeader("Authorization") String authorizationHeader
    ) {
        User user = getUserFromToken(userRepository, authorizationHeader);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String newUsername = userRequest.getUsername();
        String newEmail = userRequest.getEmail();

        if (newUsername != null && !user.getUsername().equals(newUsername) &&
                userRepository.findByUsername(newUsername).isEmpty()) {
            user.setUsername(newUsername);
        }
        if (newEmail != null && !user.getEmail().equals(newEmail) &&
                userRepository.findByEmail(newEmail).isEmpty()) {
            user.setEmail(newEmail);
        }
        if (userRequest.getProfileImageUrl() != null) {
            user.setProfileImageUrl(userRequest.getProfileImageUrl());
        }
        if (userRequest.getBio() != null) {
            user.setBio(userRequest.getBio());
        }
        if (userRequest.getFullName() != null) {
            user.setFullName(userRequest.getFullName());
        }

        userRepository.save(user);

        UserResponse response = new UserResponse(user.getId().toString(), user.getUsername(),
                user.getProfileImageUrl(), user.getFullName(), user.getBio(), user.getEmail(), false,
                followRepository.countByFolloweeId(user.getId()), followRepository.countByFollowerId(user.getId()),
                postRepository.countByOwner_Id(user.getId()));

        return ResponseEntity.ok().body(response);
    }

    @DeleteMapping("/me")
    @Transactional
    public ResponseEntity<String> deleteActiveUser(@RequestHeader("Authorization") String authorizationHeader) {
        User user = getUserFromToken(userRepository, authorizationHeader);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        postRepository.deleteByOwner(user);
        userRepository.delete(user);

        return ResponseEntity.ok("OK");
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUser(
            @RequestHeader("Authorization") String authorizationHeader, @PathVariable("userId") String userId
    ) {
        User currentUser = getUserFromToken(userRepository, authorizationHeader);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Optional<User> userOpt = userRepository.findById(UUID.fromString(userId));

        if (userOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        User user = userOpt.get();

        UserResponse response = new UserResponse(user.getId().toString(), user.getUsername(),
                user.getProfileImageUrl(), user.getFullName(), user.getBio(), user.getEmail(),
                followRepository.existsByFollowerIdAndFolloweeId(currentUser.getId(), user.getId()),
                followRepository.countByFolloweeId(user.getId()), followRepository.countByFollowerId(user.getId()),
                postRepository.countByOwner_Id(user.getId()));
        return ResponseEntity.ok().body(response);
    }

    @GetMapping("/followers/{userId}")
    public ResponseEntity<List<UserResponse>> getFollowers(@PathVariable("userId") String userId) {
        List<UserResponse> response = followRepository.findFollowersByFolloweeId(UUID.fromString(userId))
                .stream().map(user ->
                new UserResponse(user.getId().toString(), user.getUsername(), user.getProfileImageUrl(),
                        user.getFullName(), user.getBio(), "", false, 0, 0, 0)).toList();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/following/{userId}")
    public ResponseEntity<List<UserResponse>> getFollowing(@PathVariable("userId") String userId) {
        List<UserResponse> response = followRepository.findAllByFollowerId(UUID.fromString(userId))
                .stream().map(user ->
                new UserResponse(user.getId().toString(), user.getUsername(), user.getProfileImageUrl(),
                        user.getFullName(), user.getBio(), "", false, 0, 0, 0)).toList();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/follow/{userId}")
    @Transactional
    public ResponseEntity<String> followUser(
            @RequestHeader("Authorization") String authorizationHeader, @PathVariable("userId") String userId
    ) {
        User currentUser = getUserFromToken(userRepository, authorizationHeader);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Optional<User> userOpt = userRepository.findById(UUID.fromString(userId));

        if (userOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        UUID followId = null;

        User user = userOpt.get();
        UUID followerId = currentUser.getId();
        UUID followeeId = user.getId();
        if (!followRepository.existsByFollowerIdAndFolloweeId(followerId, followeeId)) {
            Follow follow = new Follow(followerId, followeeId, ZonedDateTime.now());
            followId = followRepository.save(follow).getId();
        } else {
            return ResponseEntity.ok("OK");
        }
        return followId == null ? ResponseEntity.notFound().build() : ResponseEntity.ok("OK");
    }

    @GetMapping("/unfollow/{userId}")
    @Transactional
    public ResponseEntity<String> unfollowUser(
            @RequestHeader("Authorization") String authorizationHeader, @PathVariable("userId") String userId
    ) {
        User currentUser = getUserFromToken(userRepository, authorizationHeader);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Optional<User> userOpt = userRepository.findById(UUID.fromString(userId));

        if (userOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        User user = userOpt.get();
        UUID followerId = currentUser.getId();
        UUID followeeId = user.getId();

        followRepository.deleteAllByFollowerIdAndFolloweeId(followerId, followeeId);

        return ResponseEntity.ok("OK");
    }

    @GetMapping("/block/{userId}")
    @Transactional
    public ResponseEntity<String> blockUser(
            @PathVariable("userId") String userId, @RequestHeader("Authorization") String authorizationHeader
    ) {
        User user = getUserFromToken(userRepository, authorizationHeader);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UUID blockerId = user.getId();
        UUID blockedUserId = UUID.fromString(userId);
        if (!blockedUserId.equals(blockerId) && !blockRepository.existsByUserIdAndBlockedUserId(blockerId, blockedUserId)
        ) {
            blockRepository.save(new Block(blockerId, blockedUserId, ZonedDateTime.now()));
            return ResponseEntity.ok("OK");
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }
}
