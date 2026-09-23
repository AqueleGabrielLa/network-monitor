package com.gabriel.networkmonitor.banner;

import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class BannerGrabberTest {

    @Test
    void shouldReturnBannerWhenServerSendsGreeting() {
        BannerSocketFake factory = new BannerSocketFake()
                .withBanner(22, "SSH-2.0-OpenSSH_9.6p1 Ubuntu-3\r\n");
        BannerGrabber grabber = new BannerGrabber(factory);

        String banner = grabber.grab("192.168.0.10", 22);

        assertNotNull(banner);
        assertTrue(banner.startsWith("SSH-2.0-OpenSSH_9.6p1"));
    }

    @Test
    void shouldKeepOnlyFirstLineWhenBinaryDataFollowsGreeting() {
        String polluted = "SSH-2.0-dropbear_2020.80\r\n"
                + "\u0000\u0001\u0002binary-kex-data\u00ff\u00fe";
        BannerSocketFake factory = new BannerSocketFake()
                .withBanner(22, polluted);
        BannerGrabber grabber = new BannerGrabber(factory);

        String banner = grabber.grab("192.168.0.10", 22);

        assertNotNull(banner);
        assertEquals("SSH-2.0-dropbear_2020.80", banner);
        assertFalse(banner.contains("binary"));
    }

    @Test
    void shouldReturnNullOnTimeout() {
        BannerSocketFake factory = new BannerSocketFake()
                .withTimeout(80);
        BannerGrabber grabber = new BannerGrabber(factory);

        String banner = grabber.grab("192.168.0.10", 80);

        assertNull(banner);
    }

    @Test
    void shouldSendHttpProbeWhenPassiveReadIsEmpty() {
        BannerSocketFake factory = new BannerSocketFake()
                .withProbeResponse(80, "HTTP/1.1 200 OK\r\nServer: nginx/1.25.3\r\n\r\n");
        BannerGrabber grabber = new BannerGrabber(factory);

        String banner = grabber.grab("192.168.0.10", 80);

        assertNotNull(banner);
        assertTrue(banner.contains("HTTP/1.1") || banner.contains("Server: nginx"));
    }

    @Test
    void shouldNotProbeWhenPassiveBannerAlreadyRead() {
        BannerSocketFake factory = new BannerSocketFake()
                .withBanner(22, "SSH-2.0-OpenSSH_9.6\r\n")
                .withProbeResponse(22, "HTTP/1.1 200 OK\r\n");
        BannerGrabber grabber = new BannerGrabber(factory);

        String banner = grabber.grab("192.168.0.10", 22);

        assertNotNull(banner);
        assertTrue(banner.startsWith("SSH-2.0"));
        assertFalse(banner.contains("HTTP"));
    }

    @Test
    void shouldTruncateLongBanner() {
        String longBanner = "X".repeat(1000);
        BannerSocketFake factory = new BannerSocketFake()
                .withBanner(12345, longBanner);
        BannerGrabber grabber = new BannerGrabber(factory);

        String banner = grabber.grab("192.168.0.10", 12345);

        assertNotNull(banner);
        assertTrue(banner.length() <= 512);
    }

    @Test
    void shouldReturnNullWhenConnectionRefused() {
        BannerSocketFake factory = new BannerSocketFake();
        BannerGrabber grabber = new BannerGrabber(factory);

        String banner = grabber.grab("192.168.0.10", 9999);

        assertNull(banner);
    }

    @Test
    void shouldReturnNullWhenBannerIsEmpty() {
        BannerSocketFake factory = new BannerSocketFake()
                .withOpenPort(12345);
        BannerGrabber grabber = new BannerGrabber(factory);

        String banner = grabber.grab("192.168.0.10", 12345);

        assertNull(banner);
    }
}
