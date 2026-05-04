package self.sai.stock.AlgoTrading.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import self.sai.stock.AlgoTrading.auth.AuthFilter;

/**
 * Spring Security configuration.
 *
 * Security rules:
 *   - POST /api/auth/register  — public (create account)
 *   - POST /api/auth/login     — public (get JWT)
 *   - All other endpoints      — require valid JWT in Authorization header
 *
 * Sessions are stateless; CSRF is disabled (REST API).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final AuthFilter authFilter;

    public SecurityConfig(AuthFilter authFilter) {
        this.authFilter = authFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF — not needed for stateless REST APIs
            .csrf(AbstractHttpConfigurer::disable)

            // Stateless session — no HTTP session created or used
            .sessionManagement(sm ->
                sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Authorization rules
            .authorizeHttpRequests(auth -> auth
                // Static Angular assets — always public
                .requestMatchers("/", "/index.html", "/*.js", "/*.css", "/*.map",
                                 "/favicon.ico", "/assets/**",
                                 "/*.woff2", "/*.woff", "/*.ttf", "/*.eot", "/*.png", "/*.ico").permitAll()
                // SPA fallback routes (Angular HTML5 routing)
                .requestMatchers("/login", "/dashboard").permitAll()
                // Zerodha OAuth callback — browser redirect from Kite, no JWT
                .requestMatchers(HttpMethod.GET, "/zerodha/callback").permitAll()
                // Public API
                .requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                .anyRequest().authenticated()
            )

            // Add JWT filter before Spring's UsernamePasswordAuthenticationFilter
            .addFilterBefore(authFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
