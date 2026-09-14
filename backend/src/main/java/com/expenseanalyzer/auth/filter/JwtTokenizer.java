package com.expenseanalyzer.auth.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Base64;

/**
 * JWT Token utility for extracting user ID from token.
 */
@Component
public class JwtTokenizer {

    @Value("${jwt.secret:expense-analyzer-jwt-secret-key-change-in-production}")
    private String secretKey;

    /**
     * Extract user ID from JWT token.
     *
     * @param token the JWT token
     * @return user ID string, or empty string if extraction fails
     */
    public String extractUserId(String token) {
        try {
            // JWT structure: header.payload.signature
            // Decode the payload (second part)
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return null;
            }

            String payloadBase64 = parts[1];
            byte[] payloadBytes = Base64.getUrlDecoder().decode(payloadBase64);
            String payload = new String(payloadBytes);

            // Extract user ID from payload (expected format: {"sub": "uuid", ...})
            if (payload.contains("\"sub\"")) {
                int subStart = payload.indexOf("\"sub\"") + 6;
                int subEnd = payload.indexOf(",", subStart);
                if (subEnd == -1) {
                    subEnd = payload.length();
                }
                return payload.substring(subStart, subEnd).trim();
            }
        } catch (Exception e) {
            // Token is invalid
        }

        return null;
    }
}
