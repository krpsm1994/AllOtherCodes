package self.sai.stock.AlgoTrading.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import self.sai.stock.AlgoTrading.service.UserService;

import java.util.Map;

/**
 * Public REST controller for application user authentication.
 * These endpoints are unauthenticated — no JWT required.
 *
 * POST /api/auth/register  — create a new user account
 * POST /api/auth/login     — authenticate and receive a JWT
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Registers a new application user.
     * Body: { "username": "alice", "password": "s3cret" }
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, String> body) {
        String username = body.getOrDefault("username", "").trim();
        String password = body.getOrDefault("password", "");
        Map<String, Object> result = userService.register(username, password);
        boolean success = Boolean.TRUE.equals(result.get("success"));
        return success ? ResponseEntity.ok(result) : ResponseEntity.badRequest().body(result);
    }

    /**
     * Authenticates an application user and returns a JWT on success.
     * Body: { "username": "alice", "password": "s3cret" }
     * Response (success): { "success": true, "token": "eyJ...", "username": "alice" }
     * Response (failure): { "success": false, "message": "Invalid credentials" }
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String username = body.getOrDefault("username", "").trim();
        String password = body.getOrDefault("password", "");
        Map<String, Object> result = userService.login(username, password);
        boolean success = Boolean.TRUE.equals(result.get("success"));
        return success ? ResponseEntity.ok(result) : ResponseEntity.status(401).body(result);
    }
}
