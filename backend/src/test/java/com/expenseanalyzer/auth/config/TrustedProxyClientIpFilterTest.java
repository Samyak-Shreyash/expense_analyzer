package com.expenseanalyzer.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TrustedProxyClientIpFilterTest {

    private final TrustedProxyClientIpFilter filter = new TrustedProxyClientIpFilter("s3cret");

    @Test
    void correctSecretAndValidIPv4UpdatesRemoteAddr() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.9");
        request.addHeader(TrustedProxyClientIpFilter.SECRET_HEADER, "s3cret");
        request.addHeader(TrustedProxyClientIpFilter.CLIENT_IP_HEADER, "198.51.100.7");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> remoteAddrSeen = new AtomicReference<>();

        filter.doFilter(request, response, (req, resp) ->
                remoteAddrSeen.set(req.getRemoteAddr()));

        assertThat(remoteAddrSeen.get()).isEqualTo("198.51.100.7");
    }

    @Test
    void correctSecretAndValidIPv6UpdatesRemoteAddr() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.9");
        request.addHeader(TrustedProxyClientIpFilter.SECRET_HEADER, "s3cret");
        request.addHeader(TrustedProxyClientIpFilter.CLIENT_IP_HEADER, "2001:db8::1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> remoteAddrSeen = new AtomicReference<>();

        filter.doFilter(request, response, (req, resp) ->
                remoteAddrSeen.set(req.getRemoteAddr()));

        assertThat(remoteAddrSeen.get()).isEqualTo("2001:db8::1");
    }

    @Test
    void wrongSecretKeepsOriginalRemoteAddr() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.9");
        request.addHeader(TrustedProxyClientIpFilter.SECRET_HEADER, "nope");
        request.addHeader(TrustedProxyClientIpFilter.CLIENT_IP_HEADER, "198.51.100.7");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> remoteAddrSeen = new AtomicReference<>();

        filter.doFilter(request, response, (req, resp) ->
                remoteAddrSeen.set(req.getRemoteAddr()));

        assertThat(remoteAddrSeen.get()).isEqualTo("203.0.113.9");
    }

    @Test
    void missingSecretHeaderKeepsOriginalRemoteAddr() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.9");
        request.addHeader(TrustedProxyClientIpFilter.CLIENT_IP_HEADER, "198.51.100.7");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> remoteAddrSeen = new AtomicReference<>();

        filter.doFilter(request, response, (req, resp) ->
                remoteAddrSeen.set(req.getRemoteAddr()));

        assertThat(remoteAddrSeen.get()).isEqualTo("203.0.113.9");
    }

    @Test
    void nonIpClientIpKeepsOriginalRemoteAddr() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.9");
        request.addHeader(TrustedProxyClientIpFilter.SECRET_HEADER, "s3cret");
        request.addHeader(TrustedProxyClientIpFilter.CLIENT_IP_HEADER, "evil.example.com");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> remoteAddrSeen = new AtomicReference<>();

        filter.doFilter(request, response, (req, resp) ->
                remoteAddrSeen.set(req.getRemoteAddr()));

        assertThat(remoteAddrSeen.get()).isEqualTo("203.0.113.9");
    }

    @Test
    void emptySecretKeepsOriginalRemoteAddr() throws Exception {
        TrustedProxyClientIpFilter filter = new TrustedProxyClientIpFilter("");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.9");
        request.addHeader(TrustedProxyClientIpFilter.SECRET_HEADER, "");
        request.addHeader(TrustedProxyClientIpFilter.CLIENT_IP_HEADER, "198.51.100.7");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> remoteAddrSeen = new AtomicReference<>();

        filter.doFilter(request, response, (req, resp) ->
                remoteAddrSeen.set(req.getRemoteAddr()));

        assertThat(remoteAddrSeen.get()).isEqualTo("203.0.113.9");
    }
}
