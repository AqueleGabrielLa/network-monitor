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

        int lineEnd = -1;
        for (int i = 0; i < banner.length(); i++) {
            char c = banner.charAt(i);
            if (c == '\n' || c == '\r') {
                lineEnd = i;
                break;
            }
        }
        String firstLine = cleanLine(lineEnd >= 0 ? banner.substring(0, lineEnd) : banner);
        if (firstLine.isEmpty()) {
            return null;
        }

        if (firstLine.startsWith("HTTP/")) {
            return sanitizeHttpHeaders(banner, lineEnd, firstLine);
        }

        return truncate(firstLine);
    }

    private String sanitizeHttpHeaders(String banner, int lineEnd, String firstLine) {
        StringBuilder result = new StringBuilder(firstLine);
        int pos = lineEnd;
        int headerLines = 1;

        while (pos < banner.length() && headerLines < 20 && result.length() < maxBytes) {
            if (banner.charAt(pos) == '\r') {
                pos++;
            }
            if (pos < banner.length() && banner.charAt(pos) == '\n') {
                pos++;
            }

            int nextEnd = -1;
            for (int i = pos; i < banner.length(); i++) {
                char c = banner.charAt(i);
                if (c == '\n' || c == '\r') {
                    nextEnd = i;
                    break;
                }
            }
            String line = cleanLine(nextEnd >= 0 ? banner.substring(pos, nextEnd) : banner.substring(pos));
            if (line.isEmpty()) {
                break;
            }
            result.append('\n').append(line);
            headerLines++;
            if (nextEnd < 0) {
                break;
            }
            pos = nextEnd;
        }

        return truncate(result.toString());
    }

    private String cleanLine(String line) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c >= 32 && c < 127) {
                sb.append(c);
            }
        }
        return sb.toString().trim();
    }

    private String truncate(String value) {
        if (value.length() > maxBytes) {
            return value.substring(0, maxBytes);
        }
        return value;
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
