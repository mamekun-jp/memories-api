package jp.mamekun.memories.api.controller;

import jakarta.validation.Valid;
import jp.mamekun.memories.api.model.User;
import jp.mamekun.memories.api.model.UserKey;
import jp.mamekun.memories.api.model.api.E2eStatusResponse;
import jp.mamekun.memories.api.model.api.PublicKeyRequest;
import jp.mamekun.memories.api.repository.UserKeyRepository;
import jp.mamekun.memories.api.repository.UserRepository;
import jp.mamekun.memories.api.util.JwtTokenUtil;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${app.e2e-enabled:true}")
    private boolean e2eEnabled;

    public KeyController(
            JwtTokenUtil jwtTokenUtil, UserRepository userRepository, UserKeyRepository userKeyRepository
    ) {
        this.jwtTokenUtil = jwtTokenUtil;
        this.userRepository = userRepository;
        this.userKeyRepository = userKeyRepository;
    }

    /** Lets clients check whether the server currently supports E2E messages (APP_E2E_ENABLED). */
    @GetMapping("/status")
    public ResponseEntity<E2eStatusResponse> getE2eStatus(
            @RequestHeader("Authorization") String authorizationHeader
    ) {
        User user = jwtTokenUtil.getUserFromToken(userRepository, authorizationHeader);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(new E2eStatusResponse(e2eEnabled));
    }

    @PostMapping
    public ResponseEntity<Void> uploadPublicKey(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestBody @Valid PublicKeyRequest request
    ) {
        User user = jwtTokenUtil.getUserFromToken(userRepository, authorizationHeader);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!e2eEnabled) {
            // Clients treat 410 as "encryption disabled on this server" and fall back to plaintext.
            return ResponseEntity.status(HttpStatus.GONE).build();
        }

        // Upsert: a re-upload replaces the stored key (key rotation) instead of being rejected.
        Optional<UserKey> userKeyOpt = userKeyRepository.findByUser(user);
        if (userKeyOpt.isPresent()) {
            UserKey userKey = userKeyOpt.get();
            userKey.setContent(request.getPublicKey());
            userKey.setUpdatedAt(ZonedDateTime.now());
            userKeyRepository.save(userKey);
        } else {
            userKeyRepository.save(new UserKey(user, request.getPublicKey(), ZonedDateTime.now()));
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

