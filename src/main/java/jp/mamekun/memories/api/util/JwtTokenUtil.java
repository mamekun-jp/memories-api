package jp.mamekun.memories.api.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jp.mamekun.memories.api.model.User;
import jp.mamekun.memories.api.repository.UserRepository;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Value;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import org.springframework.stereotype.Component;

@Component
@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE) // Private constructor to enforce singleton usage
public class JwtTokenUtil {

    @Value("${app.jwt.secret:szNjWVZXMoDDgH1Xl7N9dyWZYgMn3TXhbeDoCIwjFzY=}")
    private String secretKey;  // Spring-injected value

    @Value("${app.jwt.expiration:86400000}")
    private long expirationTime; // Expiration time in milliseconds

    private SecretKey key;

    @PostConstruct
    public void init() {
        if (secretKey == null || secretKey.isEmpty()) {
            throw new IllegalStateException("JWT secret key is not set properly!");
        }
        this.key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String username) {
        Date expirationDate = new Date(System.currentTimeMillis() + expirationTime);

        return Jwts.builder()
                .subject(username)
                .issuedAt(new Date())
                .expiration(expirationDate)
                .signWith(key)
                .compact();
    }

    public Claims validateToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            return null; // Return null if the token is invalid
        }
    }

    public String extractUsername(String token) {
        Claims claims = validateToken(token);
        return claims != null ? claims.getSubject() : null;
    }

    public boolean isTokenExpired(String token) {
        Claims claims = validateToken(token);
        return claims != null && claims.getExpiration().before(new Date());
    }

    public String extractToken(String authorizationHeader) {
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            return authorizationHeader.substring(7);
        }
        throw new IllegalArgumentException("Invalid Authorization header");
    }

    public User getUserFromToken(UserRepository userRepository, String authorizationHeader) {
        String token = extractToken(authorizationHeader);
        String username = extractUsername(token);

        if (username == null) {
            return null; // Return null if the token is invalid
        }

        return userRepository.findByEmail(username).orElse(null);
    }
}