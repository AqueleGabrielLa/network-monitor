package com.gabriel.networkmonitor.banner;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ServiceIdentifierTest {

    private final ServiceIdentifier identifier = new ServiceIdentifier();

    @Test
    void shouldIdentifySshFromBanner() {
        Optional<String> service = identifier.identify(22, "SSH-2.0-OpenSSH_9.6p1 Ubuntu-3");

        assertTrue(service.isPresent());
        assertTrue(service.get().startsWith("ssh"));
        assertTrue(service.get().contains("OpenSSH_9.6p1"));
    }

    @Test
    void shouldIdentifyHttpFromServerHeader() {
        Optional<String> service = identifier.identify(80,
                "HTTP/1.1 200 OK\r\nServer: nginx/1.25.3\r\nContent-Length: 0\r\n");

        assertTrue(service.isPresent());
        assertTrue(service.get().startsWith("http"));
        assertTrue(service.get().contains("nginx/1.25.3"));
    }

    @Test
    void shouldIdentifyHttpWithoutServerHeader() {
        Optional<String> service = identifier.identify(8080, "HTTP/1.1 404 Not Found");

        assertTrue(service.isPresent());
        assertEquals("http", service.get());
    }

    @Test
    void shouldIdentifySmtpFromEsmtpBanner() {
        Optional<String> service = identifier.identify(25, "220 mail.example.com ESMTP Postfix");

        assertTrue(service.isPresent());
        assertTrue(service.get().startsWith("smtp"));
        assertTrue(service.get().contains("Postfix"));
    }

    @Test
    void shouldFallbackToKnownPortWhenBannerIsNull() {
        Optional<String> service = identifier.identify(22, null);

        assertTrue(service.isPresent());
        assertEquals("ssh", service.get());
    }

    @Test
    void shouldFallbackToKnownPortWhenBannerUnparseable() {
        Optional<String> service = identifier.identify(443, "garbage banner");

        assertTrue(service.isPresent());
        assertEquals("https", service.get());
    }

    @Test
    void shouldReturnEmptyForUnknownPortAndNoBanner() {
        Optional<String> service = identifier.identify(54321, null);

        assertTrue(service.isEmpty());
    }

    @Test
    void shouldIdentifyFtpFromBanner() {
        Optional<String> service = identifier.identify(21, "220 (vsFTPd 3.0.5)");

        assertTrue(service.isPresent());
        assertTrue(service.get().contains("vsFTPd"));
    }

    @Test
    void shouldIdentifyMysqlFromBanner() {
        Optional<String> service = identifier.identify(3306, "J�mysql_native_password");

        assertTrue(service.isPresent());
        assertEquals("mysql", service.get());
    }
}
