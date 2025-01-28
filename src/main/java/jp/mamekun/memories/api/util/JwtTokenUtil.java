package jp.mamekun.memories.api.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jp.mamekun.memories.api.model.User;
import jp.mamekun.memories.api.repository.UserRepository;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class JwtTokenUtil {

    private static final String SECRET_KEY = "szNjWVZXMoDDgH1Xl7N9dyWZYgMn3TXhbeDoCIwjFzY=";  // Replace with your actual secret key
    private static final long EXPIRATION_TIME = 86400000L; // 24 hours in milliseconds

    // Generate JWT Token
    public static String generateToken(String username) {
        Date expirationDate = new Date(System.currentTimeMillis() + EXPIRATION_TIME);

        // Generate SecretKey using the updated method
        SecretKey secretKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET_KEY));

        // Build the token using JwtBuilder
        return Jwts.builder()
                .claims(Jwts.claims().subject(username).build())  // Set subject as the username
                .issuedAt(new Date())  // Set issued at date
                .expiration(expirationDate)  // Set expiration date
                .signWith(secretKey)  // Sign with the secret key using HMAC
                .compact();  // Return the compact JWT token
    }

    // Validate JWT Token and parse claims
    public static Claims validateToken(String token) {
        try {
            // Decode the secret key
            SecretKey secretKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET_KEY));

            // Parse the JWT and get the claims
            return Jwts.parser()
                    .verifyWith(secretKey)  // Set the signing key
                    .build()
                    .parseSignedClaims(token)  // Parse the JWT token
                    .getPayload();  // Return the claims body
        } catch (Exception e) {
            // Handle the case where the token is invalid
            return null;  // Return null if token is invalid
        }
    }

    // Extract username from JWT token
    public static String extractUsername(String token) {
        Claims claims = validateToken(token);
        return claims != null ? claims.getSubject() : null;  // Return the username from the claims
    }

    // Check if the token is expired
    public static boolean isTokenExpired(String token) {
        Claims claims = validateToken(token);
        if (claims != null) {
            Date expiration = claims.getExpiration();
            return expiration.before(new Date());  // Check if the token is expired
        }
        return true;  // Token is expired if claims are null
    }

    public static String extractToken(String authorizationHeader) {
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            return authorizationHeader.substring(7); // Remove the "Bearer " prefix
        }
        throw new IllegalArgumentException("Invalid Authorization header");
    }

    public static User getUserFromToken(UserRepository userRepository, String authorizationHeader) {
        // Extract the token from the "Bearer <token>" header
        String token = extractToken(authorizationHeader);

        // Use JwtTokenUtil to extract the username
        String username = extractUsername(token);

        if (username == null) {
            return null; // Return 401 if the token is invalid
        }

        Optional<User> userOpt = userRepository.findByEmail(username);
        return userOpt.orElse(null);
    }
}
