package self.sai.stock.AlgoTrading.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Creates and validates HMAC-SHA256 JWTs for app-level authentication.
 *
 * Configuration keys in application.properties:
 *   jwt.secret       — at least 32 characters; change before deploying
 *   jwt.expiry.hours — token lifetime in hours (default 24)
 */
@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

    @Value("${jwt.secret:AlGoTrAdInGsEcReTkEy2026MuStBe32CharsLong!}")
    private String secret;

    @Value("${jwt.expiry.hours:24}")
    private long expiryHours;

    private SecretKey key;
    private long      expiryMs;

    @PostConstruct
    public void init() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.key        = Keys.hmacShaKeyFor(keyBytes);
        this.expiryMs   = expiryHours * 60 * 60 * 1_000L;
        log.info("JwtUtil initialised — expiry={}h", expiryHours);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Generates a signed JWT for the given username.
     *
     * @param username the authenticated user's username
     * @return compact JWT string
     */
    public String generateToken(String username) {
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + expiryMs);

        return Jwts.builder()
                .subject(username)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    /**
     * Validates the JWT and returns the username (subject) if valid.
     *
     * @param token compact JWT string (may include "Bearer " prefix — stripped automatically)
     * @return username encoded in the token
     * @throws JwtException if the token is invalid, expired, or tampered with
     */
    public String validateAndGetUsername(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getSubject();
    }

    /**
     * Returns {@code true} if the token is valid and not expired.
     * Does not throw — safe to use in filter chains.
     */
    public boolean isValid(String token) {
        try {
            validateAndGetUsername(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
