package com.expenseanalyzer.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthResponse(String token, String refreshToken) {

    /** Web path: the refresh token travels in the cookie, not the body. */
    public AuthResponse(String token) {
        this(token, null);
    }

    @Override
    public String toString() {
        return "AuthResponse[token=***, refreshToken=***]";
    }
}
