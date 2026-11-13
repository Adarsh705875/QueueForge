package queueforge.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {
    private final byte[] expectedKey;

    public ApiKeyAuthenticationFilter(String expectedKey) {
        if (expectedKey == null || expectedKey.length() < 32 || expectedKey.startsWith("REPLACE_") || expectedKey.startsWith("CHANGE_ME")) {
            throw new IllegalStateException("QUEUEFORGE_API_KEY must contain at least 32 characters");
        }
        this.expectedKey = expectedKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String supplied = request.getHeader("X-API-Key");
        if (supplied == null || !MessageDigest.isEqual(expectedKey, supplied.getBytes(StandardCharsets.UTF_8))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setCharacterEncoding("UTF-8");
            response.setContentType("application/json");
            response.setHeader("Cache-Control", "no-store");
            response.getWriter().write("{\"error\":\"unauthorized\",\"message\":\"A valid X-API-Key header is required\"}");
            return;
        }
        var authentication = new UsernamePasswordAuthenticationToken(
                "queueforge-api-client", null, List.of(new SimpleGrantedAuthority("ROLE_API_CLIENT")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }
}
