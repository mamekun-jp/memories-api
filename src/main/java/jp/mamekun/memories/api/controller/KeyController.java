package jp.mamekun.memories.api.controller;

import jp.mamekun.memories.api.model.User;
import jp.mamekun.memories.api.model.UserKey;
import jp.mamekun.memories.api.repository.UserKeyRepository;
import jp.mamekun.memories.api.repository.UserRepository;
import jp.mamekun.memories.api.util.JwtTokenUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/key")
public class KeyController {
    private final JwtTokenUtil jwtTokenUtil;
    private final UserRepository userRepository;
    private final UserKeyRepository userKeyRepository;

    public KeyController(
            JwtTokenUtil jwtTokenUtil, UserRepository userRepository, UserKeyRepository userKeyRepository
    ) {
        this.jwtTokenUtil = jwtTokenUtil;
        this.userRepository = userRepository;
        this.userKeyRepository = userKeyRepository;
    }

    @PostMapping
    public ResponseEntity<Void> uploadPublicKey(
            @RequestHeader("Authorization") String authorizationHeader, @RequestBody String publicKey
    ) {
        User user = jwtTokenUtil.getUserFromToken(userRepository, authorizationHeader);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Optional<UserKey> userKeyOpt = userKeyRepository.findByUser(user);
        if (userKeyOpt.isEmpty()) {
            UserKey userKey = new UserKey(user, publicKey, ZonedDateTime.now());
            userKeyRepository.save(userKey);
        } else {
            return ResponseEntity.status(HttpStatus.ALREADY_REPORTED).build();
        }

        return ResponseEntity.ok().build();
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<String> getPublicKey(@PathVariable String userId) {
        Optional<UserKey> userKeyOpt = userKeyRepository.findByUser_Id(UUID.fromString(userId));
        return userKeyOpt.map(userKey -> ResponseEntity.ok(userKey.getContent())).orElseGet(() ->
                ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }
}

