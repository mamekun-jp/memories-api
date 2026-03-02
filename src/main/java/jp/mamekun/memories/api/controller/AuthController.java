package jp.mamekun.memories.api.controller;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jp.mamekun.memories.api.model.PasswordResetToken;
import jp.mamekun.memories.api.model.User;
import jp.mamekun.memories.api.model.api.LoginRequest;
import jp.mamekun.memories.api.model.api.LoginResponse;
import jp.mamekun.memories.api.model.api.ResetPasswordConfirmRequest;
import jp.mamekun.memories.api.model.api.ResetPasswordRequest;
import jp.mamekun.memories.api.model.api.SignupRequest;
import jp.mamekun.memories.api.repository.PasswordResetTokenRepository;
import jp.mamekun.memories.api.repository.UserRepository;
import jp.mamekun.memories.api.service.EmailService;
import jp.mamekun.memories.api.util.JwtTokenUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JwtTokenUtil jwtTokenUtil;
    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    @Value("${app.mail.from:no-reply@example.com}")
    private String mailFrom;

    /**
     * Base URL of your front-end reset password page, e.g. https://app.example.com/reset-password
     * The token will be appended as ?token=...
     */
    @Value("${app.password-reset.base-url:http://localhost:3000/reset-password}")
    private String passwordResetBaseUrl;

    public AuthController(
            JwtTokenUtil jwtTokenUtil, AuthenticationManager authenticationManager, UserRepository userRepository,
            PasswordEncoder passwordEncoder, EmailService emailService,
            PasswordResetTokenRepository passwordResetTokenRepository
    ) {
        this.jwtTokenUtil = jwtTokenUtil;
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
    }

    // User Registration
    @PostMapping("/register")
    @Transactional
    public ResponseEntity<LoginResponse> register(@RequestBody @Valid SignupRequest signupRequest) {
        // Check if user already exists
        if (userRepository.existsByEmail(signupRequest.getEmail()) ||
                userRepository.existsByUsername(signupRequest.getUsername())) {
            return ResponseEntity.badRequest().build();
        }

        String hashedPassword = passwordEncoder.encode(signupRequest.getPassword());
        User newUser = new User(false, false, signupRequest.getUsername(),
                signupRequest.getEmail(), hashedPassword);

        // Save the user to database
        userRepository.save(newUser);

        // Generate JWT Token
        String token = jwtTokenUtil.generateToken(signupRequest.getEmail());

        // Return the token wrapped in LoginResponse
        return ResponseEntity.ok(new LoginResponse(token));
    }

    // User Login
    @PostMapping({"/login"})
    public ResponseEntity<LoginResponse> login(@RequestBody @Valid LoginRequest loginRequest) {
        // Authenticate user
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getEmail(),
                        loginRequest.getPassword()
                )
        );

        // Generate JWT Token
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String token = jwtTokenUtil.generateToken(userDetails.getUsername());

        // Return the token wrapped in LoginResponse
        return ResponseEntity.ok(new LoginResponse(token));
    }

    // Password reset request
    // Password reset request
    @PostMapping({"/forgot-password"})
    @Transactional
    public ResponseEntity<String> passwordResetRequest(
            @RequestBody @Valid ResetPasswordRequest resetPasswordRequest
    ) {
        // Always respond OK (prevents email/user enumeration).
        Optional<User> userOpt = userRepository.findByEmail(resetPasswordRequest.getEmail());

        if (userOpt.isEmpty()) {
            return ResponseEntity.ok("OK");
        }

        User user = userOpt.get();

        // Invalidate previous reset tokens for the same user (simple + effective).
        passwordResetTokenRepository.deleteByUserId(user.getId());

        // Create a new token (raw token never stored in DB).
        String rawToken = generateSecureToken(32);
        String tokenHash = sha256Hex(rawToken);

        ZonedDateTime now = ZonedDateTime.now();
        PasswordResetToken prt = new PasswordResetToken();
        prt.setUserId(user.getId());
        prt.setTokenHash(tokenHash);
        prt.setCreatedAt(now);
        prt.setExpiresAt(now.plus(30, ChronoUnit.MINUTES));

        passwordResetTokenRepository.save(prt);

        String resetLink = passwordResetBaseUrl + "?token=" + rawToken;

        String subject = "Password reset request";
        String body = """
                We received a request to reset your password.

                Reset link (valid for 30 minutes):
                %s

                If you didn’t request this, you can ignore this email.
                """.formatted(resetLink);

        // If email sending fails, we still return OK to the caller.
        try {
            emailService.sendTextMail(mailFrom, user.getEmail(), subject, body);
        } catch (Exception ignored) {
            // Intentionally ignored: avoid leaking delivery status.
        }

        return ResponseEntity.ok("OK");
    }

    private static String generateSecureToken(int bytes) {
        byte[] buf = new byte[bytes];
        new SecureRandom().nextBytes(buf);
        return HexFormat.of().formatHex(buf);
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            // Should never happen on a standard JVM; rethrow as unchecked to fail fast.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Completes the password reset using the token that was emailed to the user.
     *
     * Front-end flow:
     *  1) user clicks emailed link: /reset-password?token=...
     *  2) user enters new password
     *  3) FE calls this endpoint with { token, newPassword }
     */
    @PostMapping({"/reset-password"})
    @Transactional
    public ResponseEntity<String> resetPassword(
            @RequestBody @Valid ResetPasswordConfirmRequest request
    ) {
        ZonedDateTime now = ZonedDateTime.now();

        String tokenHash = sha256Hex(request.getToken());
        Optional<PasswordResetToken> tokenOpt = passwordResetTokenRepository.findByTokenHash(tokenHash);

        // Respond generically to avoid leaking whether token exists/valid.
        if (tokenOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("Invalid or expired token");
        }

        PasswordResetToken token = tokenOpt.get();

        if (token.isUsed() || token.isExpired(now)) {
            return ResponseEntity.badRequest().body("Invalid or expired token");
        }

        UUID userId = token.getUserId();
        Optional<User> userOpt = userRepository.findUserById(userId);

        if (userOpt.isEmpty()) {
            // Token exists but user is missing; treat as invalid.
            return ResponseEntity.badRequest().body("Invalid or expired token");
        }

        User user = userOpt.get();
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        token.setUsedAt(now);
        passwordResetTokenRepository.save(token);

        return ResponseEntity.ok("OK");
    }
}
