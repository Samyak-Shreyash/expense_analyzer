package com.expenseanalyzer.auth.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Behind the Vercel proxy every request comes from Vercel's IPs, so per-IP rate limits would be
 * shared by all users. The proxy sends the user's IP with a shared secret; only when the secret
 * matches is that IP used as the remote address. Disabled when no secret is configured.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class TrustedProxyClientIpFilter extends OncePerRequestFilter {

    public static final String SECRET_HEADER = "X-Proxy-Secret";
    public static final String CLIENT_IP_HEADER = "X-Client-IP";
    // IPv4 or IPv6 literal only; never resolved, so no DNS lookups.
    private static final Pattern IP_LITERAL = Pattern.compile("[0-9A-Fa-f:.]{2,45}");

    private final byte[] secret;

    public TrustedProxyClientIpFilter(@Value("${app.rate-limit.proxy-secret:}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String clientIp = request.getHeader(CLIENT_IP_HEADER);
        if (trusted(request.getHeader(SECRET_HEADER)) && clientIp != null && IP_LITERAL.matcher(clientIp).matches()) {
            filterChain.doFilter(new HttpServletRequestWrapper(request) {
                @Override
                public String getRemoteAddr() {
                    return clientIp;
                }
            }, response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean trusted(String presented) {
        return secret.length > 0 && presented != null
                && MessageDigest.isEqual(secret, presented.getBytes(StandardCharsets.UTF_8));
    }
}
