package jp.mamekun.memories.api.controller;

import jakarta.transaction.Transactional;
import jp.mamekun.memories.api.config.SpaceSettings;
import jp.mamekun.memories.api.model.Invite;
import jp.mamekun.memories.api.model.User;
import jp.mamekun.memories.api.model.api.InviteRequest;
import jp.mamekun.memories.api.model.api.InviteResponse;
import jp.mamekun.memories.api.repository.InviteRepository;
import jp.mamekun.memories.api.repository.UserRepository;
import jp.mamekun.memories.api.util.JwtTokenUtil;
import jp.mamekun.memories.api.util.TimeFormat;
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

import java.security.SecureRandom;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.List;

/** Space admins (APP_ADMIN_EMAILS) create, list and revoke invites. */
@RestController
@RequestMapping("/api/invites")
public class InviteController {
    private static final int DEFAULT_HOURS = 72;
    private static final int MAX_HOURS = 24 * 30;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JwtTokenUtil jwtTokenUtil;
    private final UserRepository userRepository;
    private final InviteRepository inviteRepository;
    private final SpaceSettings spaceSettings;

    public InviteController(JwtTokenUtil jwtTokenUtil, UserRepository userRepository,
                            InviteRepository inviteRepository, SpaceSettings spaceSettings) {
        this.jwtTokenUtil = jwtTokenUtil;
        this.userRepository = userRepository;
        this.inviteRepository = inviteRepository;
        this.spaceSettings = spaceSettings;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<InviteResponse> createInvite(
            @RequestBody(required = false) InviteRequest request, @RequestHeader("Authorization") String authorizationHeader
    ) {
        User admin = adminFrom(authorizationHeader);
        if (admin == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        int hours = request == null || request.getExpiresInHours() == null ? DEFAULT_HOURS : request.getExpiresInHours();
        if (hours < 1 || hours > MAX_HOURS) {
            return ResponseEntity.badRequest().build();
        }
        ZonedDateTime now = ZonedDateTime.now();
        Invite invite = inviteRepository.save(new Invite(newToken(), admin.getId(), now, now.plusHours(hours)));
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(invite, now));
    }

    @GetMapping
    public ResponseEntity<List<InviteResponse>> listInvites(@RequestHeader("Authorization") String authorizationHeader) {
        if (adminFrom(authorizationHeader) == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        ZonedDateTime now = ZonedDateTime.now();
        return ResponseEntity.ok(inviteRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(invite -> toResponse(invite, now)).toList());
    }

    @DeleteMapping("/{token}")
    @Transactional
    public ResponseEntity<Void> revokeInvite(
            @PathVariable String token, @RequestHeader("Authorization") String authorizationHeader
    ) {
        if (adminFrom(authorizationHeader) == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        inviteRepository.findByToken(token).ifPresent(invite -> {
            invite.setRevoked(true);
            inviteRepository.save(invite);
        });
        return ResponseEntity.noContent().build();
    }

    private User adminFrom(String authorizationHeader) {
        User user = jwtTokenUtil.getUserFromToken(userRepository, authorizationHeader);
        return spaceSettings.isAdmin(user) ? user : null;
    }

    private InviteResponse toResponse(Invite invite, ZonedDateTime now) {
        String usedBy = invite.getUsedBy() == null ? null
                : userRepository.findById(invite.getUsedBy()).map(User::getUsername).orElse(null);
        return new InviteResponse(invite.getToken(), TimeFormat.iso(invite.getCreatedAt()),
                TimeFormat.iso(invite.getExpiresAt()), invite.status(now), usedBy);
    }

    /** 128 random bits, URL-safe. */
    private static String newToken() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
