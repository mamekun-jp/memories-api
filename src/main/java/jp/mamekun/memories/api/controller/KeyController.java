package jp.mamekun.memories.api.controller;

import jp.mamekun.memories.api.model.User;
import jp.mamekun.memories.api.model.UserKey;
import jp.mamekun.memories.api.repository.UserKeyRepository;
import jp.mamekun.memories.api.repository.UserRepository;
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

import static jp.mamekun.memories.api.util.JwtTokenUtil.getUserFromToken;

@RestController
@RequestMapping("/api/key")
public class KeyController {
    private final UserRepository userRepository;
    private final UserKeyRepository userKeyRepository;

    public KeyController(UserRepository userRepository, UserKeyRepository userKeyRepository) {
        this.userRepository = userRepository;
        this.userKeyRepository = userKeyRepository;
    }

    @PostMapping
    public ResponseEntity<Void> uploadPublicKey(
            @RequestHeader("Authorization") String authorizationHeader, @RequestBody String publicKey
    ) {
        User user = getUserFromToken(userRepository, authorizationHeader);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Optional<UserKey> userKeyOpt = userKeyRepository.findByUser(user);
        if (userKeyOpt.isEmpty()) {
            UserKey userKey = new UserKey(user, publicKey, ZonedDateTime.now());
            userKeyRepository.save(userKey);
        } else {
            /*UserKey userKey = userKeyOpt.get();
            userKey.setContent(publicKey);
            userKey.setUpdatedAt(ZonedDateTime.now());
            userKeyRepository.save(userKey);*/
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

