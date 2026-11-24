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
        assertEquals("ssh", service.get());
    }

    @Test
    void shouldExtractSshVersionFromBanner() {
        String version = ServiceIdentifier.versionFrom("SSH-2.0-OpenSSH_9.6p1 Ubuntu-3");

        assertNotNull(version);
        assertTrue(version.contains("OpenSSH_9.6p1"));
    }

    @Test
    void shouldIgnoreBinaryJunkAfterSshGreeting() {
        Optional<String> service = identifier.identify(22,
                "SSH-2.0-dropbear_2020.80\n\u0000\u0001binary junk");

        assertTrue(service.isPresent());
        assertEquals("ssh", service.get());
        assertEquals("dropbear_2020.80", ServiceIdentifier.versionFrom(
                "SSH-2.0-dropbear_2020.80\n\u0000\u0001binary junk"));
    }

    @Test
    void shouldIdentifyHttpFromServerHeader() {
        Optional<String> service = identifier.identify(80,
                "HTTP/1.1 200 OK\r\nServer: nginx/1.25.3\r\nContent-Length: 0\r\n");

        assertTrue(service.isPresent());
        assertEquals("http", service.get());
        assertEquals("nginx/1.25.3", ServiceIdentifier.versionFrom(
                "HTTP/1.1 200 OK\r\nServer: nginx/1.25.3\r\nContent-Length: 0\r\n"));
    }

    @Test
    void shouldIdentifyHttpWithoutServerHeader() {
        Optional<String> service = identifier.identify(8080, "HTTP/1.1 404 Not Found");

        assertTrue(service.isPresent());
        assertEquals("http", service.get());
        assertNull(ServiceIdentifier.versionFrom("HTTP/1.1 404 Not Found"));
    }

    @Test
    void shouldIdentifySmtpFromEsmtpBanner() {
        Optional<String> service = identifier.identify(25, "220 mail.example.com ESMTP Postfix");

        assertTrue(service.isPresent());
        assertEquals("smtp", service.get());
        assertEquals("Postfix", ServiceIdentifier.versionFrom("220 mail.example.com ESMTP Postfix"));
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
        assertEquals("ftp", service.get());
        assertEquals("vsFTPd 3.0.5", ServiceIdentifier.versionFrom("220 (vsFTPd 3.0.5)"));
    }

    @Test
    void shouldIdentifyMysqlFromBanner() {
        Optional<String> service = identifier.identify(3306, "J�mysql_native_password");

        assertTrue(service.isPresent());
        assertEquals("mysql", service.get());
    }

    @Test
    void shouldComposeDisplayLabelWithServiceAndVersion() {
        String label = ServiceIdentifier.displayLabel(22,
                "SSH-2.0-OpenSSH_9.6p1 Ubuntu-3", "ssh");

        assertEquals("ssh OpenSSH_9.6p1 Ubuntu-3", label);
    }

    @Test
    void shouldComposeDisplayLabelWithServiceOnlyWhenNoVersion() {
        String label = ServiceIdentifier.displayLabel(443, "garbage", "https");

        assertEquals("https", label);
    }

    @Test
    void shouldResolveServiceFromBannerWhenServiceFieldIsNull() {
        String label = ServiceIdentifier.displayLabel(22,
                "SSH-2.0-OpenSSH_9.6p1", null);

        assertEquals("ssh OpenSSH_9.6p1", label);
    }
}
