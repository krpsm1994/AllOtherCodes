package self.sai.stock.AlgoTrading.service;

import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import self.sai.stock.AlgoTrading.entity.User;
import self.sai.stock.AlgoTrading.repository.UserRepository;
import self.sai.stock.AlgoTrading.util.JwtUtil;

import java.util.Map;
import java.util.Optional;

/**
 * Handles application-level user authentication backed by the {@code users} table.
 *
 * Passwords are hashed with BCrypt before storage.
 * A successful login returns a signed JWT valid for {@code jwt.expiry.hours} hours.
 * All subsequent API calls must supply this token in the {@code Authorization: Bearer <token>} header.
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final JwtUtil        jwtUtil;

    public UserService(UserRepository userRepository, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.jwtUtil        = jwtUtil;
    }

    // ── Public API ──────────────────────────────────────────────────────────────────

    /**
     * Registers a new user. Fails if the username already exists.
     *
     * @return result map with {@code success} boolean and {@code message}
     */
    public Map<String, Object> register(String username, String rawPassword) {
        if (username == null || username.isBlank() || rawPassword == null || rawPassword.isBlank()) {
            return Map.of("success", false, "message", "username and password are required");
        }
        if (userRepository.findByUsername(username).isPresent()) {
            return Map.of("success", false, "message", "Username already exists");
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(BCrypt.hashpw(rawPassword, BCrypt.gensalt()));
        user.setStatus("ACTIVE");
        userRepository.save(user);
        log.info("Registered new user '{}'", username);
        return Map.of("success", true, "message", "User registered successfully");
    }

    /**
     * Authenticates a user. Returns a JWT on success.
     *
     * @return map — on success: {@code success=true, token=<JWT>, username=<name>};
     *               on failure: {@code success=false, message=<reason>}
     */
    public Map<String, Object> login(String username, String rawPassword) {
        if (username == null || username.isBlank() || rawPassword == null || rawPassword.isBlank()) {
            return Map.of("success", false, "message", "username and password are required");
        }
        Optional<User> found = userRepository.findByUsername(username);
        if (found.isEmpty()) {
            log.warn("Login attempt for unknown user '{}'", username);
            return Map.of("success", false, "message", "Invalid credentials");
        }
        User user = found.get();
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            log.warn("Login attempt for inactive user '{}'", username);
            return Map.of("success", false, "message", "Account is not active");
        }
        boolean passwordMatches;
        try {
            passwordMatches = BCrypt.checkpw(rawPassword, user.getPassword());
        } catch (IllegalArgumentException e) {
            // Stored hash is not a valid BCrypt hash (e.g. plain-text inserted directly into DB)
            log.warn("Stored password for user '{}' is not a valid BCrypt hash — rejecting login", username);
            return Map.of("success", false, "message", "Invalid credentials");
        }
        if (!passwordMatches) {
            log.warn("Invalid password for user '{}'", username);
            return Map.of("success", false, "message", "Invalid credentials");
        }
        String token = jwtUtil.generateToken(username);
        log.info("Login successful for user '{}'", username);
        return Map.of("success", true, "token", token, "username", username);
    }

    /**
     * Validates a JWT token.
     *
     * @param token the compact JWT (with or without "Bearer " prefix)
     * @return map with {@code valid} (boolean) and either {@code username} or {@code message}
     */
    public Map<String, Object> verifyToken(String token) {
        if (token == null || token.isBlank()) {
            return Map.of("valid", false, "message", "Token is missing");
        }
        try {
            String username = jwtUtil.validateAndGetUsername(token);
            return Map.of("valid", true, "username", username);
        } catch (Exception e) {
            return Map.of("valid", false, "message", "Invalid or expired token");
        }
    }
}
