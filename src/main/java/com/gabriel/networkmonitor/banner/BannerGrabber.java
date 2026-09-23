package com.gabriel.networkmonitor.banner;

import com.gabriel.networkmonitor.config.AppConfig;
import com.gabriel.networkmonitor.interfaces.SocketFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class BannerGrabber {

    private static final Logger logger = LoggerFactory.getLogger(BannerGrabber.class);

    private static final String HTTP_PROBE = "GET / HTTP/1.0\r\nHost: %s\r\n\r\n";

    private final SocketFactory socketFactory;
    private final boolean enabled;
    private final int timeoutMs;
    private final int maxBytes;
    private final Set<Integer> probePorts;

    public BannerGrabber(SocketFactory socketFactory) {
        this.socketFactory = socketFactory;
        this.enabled = AppConfig.getBoolean("banner.enabled", true);
        this.timeoutMs = AppConfig.getInt("banner.timeout", 1500);
        this.maxBytes = AppConfig.getInt("banner.max-bytes", 512);
        this.probePorts = parsePorts(AppConfig.getString("banner.probe-ports", "80,8080,8000,443,8443"));
    }

    public String grab(String host, int port) {
        if (!enabled) {
            return null;
        }

        try (Socket socket = socketFactory.create(host, port)) {
            socket.setSoTimeout(timeoutMs);

            String banner = readBanner(socket.getInputStream());

            if (banner == null && probePorts.contains(port)) {
                writeProbe(socket, host);
                banner = readBanner(socket.getInputStream());
            }

            return sanitize(banner);
        } catch (SocketTimeoutException e) {
            logger.debug("Timeout ao capturar banner de {}:{}", host, port);
            return null;
        } catch (IOException e) {
            logger.debug("Falha ao capturar banner de {}:{} - {}", host, port, e.getMessage());
            return null;
        }
    }

    private String readBanner(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        int first;
        try {
            first = in.read();
        } catch (SocketTimeoutException e) {
            return buffer.size() > 0 ? buffer.toString(StandardCharsets.UTF_8) : null;
        }
        if (first == -1) {
            return null;
        }
        buffer.write(first);

        while (buffer.size() < maxBytes) {
            int available = in.available();
            if (available <= 0) {
                break;
            }
            int toRead = Math.min(available, maxBytes - buffer.size());
            byte[] chunk = in.readNBytes(toRead);
            if (chunk.length == 0) {
                break;
            }
            buffer.write(chunk, 0, chunk.length);
        }

        return buffer.toString(StandardCharsets.UTF_8);
    }

    private void writeProbe(Socket socket, String host) throws IOException {
        OutputStream out = socket.getOutputStream();
        out.write(String.format(HTTP_PROBE, host).getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private String sanitize(String banner) {
        if (banner == null) {
            return null;
        }
        String cleaned = banner
                .replaceAll("[\\x00-\\x1f\\x7f]", " ")
                .trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        if (cleaned.length() > maxBytes) {
            cleaned = cleaned.substring(0, maxBytes);
        }
        int newline = cleaned.indexOf('\n');
        if (newline > 0) {
            cleaned = cleaned.substring(0, newline).trim();
        }
        return cleaned.isEmpty() ? null : cleaned;
    }

    private static Set<Integer> parsePorts(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Integer::parseInt)
                .collect(Collectors.toUnmodifiableSet());
    }
}
