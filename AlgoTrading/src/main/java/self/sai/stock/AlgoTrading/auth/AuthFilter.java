package self.sai.stock.AlgoTrading.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import self.sai.stock.AlgoTrading.util.JwtUtil;

import java.io.IOException;
import java.util.Collections;

/**
 * JWT authentication filter. Runs once per request before Spring Security's
 * standard filter chain. Extracts and validates the {@code Authorization: Bearer <token>}
 * header; on success, populates the {@link SecurityContextHolder} so downstream
 * security decisions work correctly.
 *
 * Public paths ({@code /api/auth/**}) are excluded in {@code SecurityConfig} and
 * therefore this filter has no effect on them.
 */
@Component
public class AuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthFilter.class);

    private final JwtUtil jwtUtil;

    public AuthFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest  request,
                                    HttpServletResponse response,
                                    FilterChain         chain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        // EventSource (SSE) cannot set headers — accept token as ?token= query param
        if (authHeader == null) {
            String queryToken = request.getParameter("token");
            if (queryToken != null && !queryToken.isBlank()) {
                authHeader = "Bearer " + queryToken;
            }
        }

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                String username = jwtUtil.validateAndGetUsername(authHeader);
                if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(
                                    username, null, Collections.emptyList());
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    log.debug("Authenticated request from '{}'", username);
                }
            } catch (Exception e) {
                log.warn("Invalid JWT on {}: {}", request.getRequestURI(), e.getMessage());
                // Don't set auth — Spring Security will reject the request
            }
        }

        chain.doFilter(request, response);
    }
}
