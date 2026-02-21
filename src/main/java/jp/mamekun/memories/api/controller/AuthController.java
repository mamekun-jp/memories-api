package jp.mamekun.memories.api.controller;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jp.mamekun.memories.api.model.User;
import jp.mamekun.memories.api.model.api.LoginRequest;
import jp.mamekun.memories.api.model.api.LoginResponse;
import jp.mamekun.memories.api.model.api.ResetPasswordRequest;
import jp.mamekun.memories.api.model.api.SignupRequest;
import jp.mamekun.memories.api.repository.UserRepository;
import jp.mamekun.memories.api.service.EmailService;
import jp.mamekun.memories.api.util.JwtTokenUtil;
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

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JwtTokenUtil jwtTokenUtil;
    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    public AuthController(
            JwtTokenUtil jwtTokenUtil, AuthenticationManager authenticationManager, UserRepository userRepository,
            PasswordEncoder passwordEncoder, EmailService emailService
    ) {
        this.jwtTokenUtil = jwtTokenUtil;
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
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
    @PostMapping({"/forgot-password"})
    public ResponseEntity<String> passwordResetRequest(
            @RequestBody @Valid ResetPasswordRequest resetPasswordRequest
    ) {
        // TODO: Finish the logic

        // Return the token wrapped in LoginResponse
        return ResponseEntity.ok("OK");
    }
}
