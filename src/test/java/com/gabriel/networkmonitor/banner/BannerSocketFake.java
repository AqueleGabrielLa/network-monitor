package com.gabriel.networkmonitor.banner;

import com.gabriel.networkmonitor.interfaces.SocketFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

class BannerSocketFake implements SocketFactory {

    private final Set<Integer> openPorts = new HashSet<>();
    private final Map<Integer, String> banners = new HashMap<>();
    private final Map<Integer, String> probeResponses = new HashMap<>();
    private final Set<Integer> timeoutPorts = new HashSet<>();

    BannerSocketFake withOpenPort(int port) {
        openPorts.add(port);
        return this;
    }

    BannerSocketFake withBanner(int port, String banner) {
        openPorts.add(port);
        banners.put(port, banner);
        return this;
    }

    BannerSocketFake withProbeResponse(int port, String response) {
        openPorts.add(port);
        probeResponses.put(port, response);
        return this;
    }

    BannerSocketFake withTimeout(int port) {
        timeoutPorts.add(port);
        return this;
    }

    @Override
    public Socket create(String host, int port) throws IOException {
        if (timeoutPorts.contains(port)) {
            throw new SocketTimeoutException("connect timed out");
        }
        if (!openPorts.contains(port)) {
            throw new IOException("Connection refused");
        }
        String banner = banners.getOrDefault(port, "");
        String probeResponse = probeResponses.get(port);
        return new ScriptedSocket(banner, probeResponse);
    }

    private static final class ScriptedSocket extends Socket {

        private final byte[] initial;
        private final byte[] probeResponse;
        private final ProbeOutputStream out;

        ScriptedSocket(String initialBanner, String probeResponse) {
            this.initial = initialBanner == null
                    ? new byte[0]
                    : initialBanner.getBytes(StandardCharsets.UTF_8);
            this.probeResponse = probeResponse == null
                    ? new byte[0]
                    : probeResponse.getBytes(StandardCharsets.UTF_8);
            this.out = new ProbeOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return new ScriptedInputStream();
        }

        @Override
        public OutputStream getOutputStream() {
            return out;
        }

        @Override
        public synchronized void setSoTimeout(int timeout) {
            // no-op for tests
        }

        private final class ProbeOutputStream extends OutputStream {
            private final ByteArrayOutputStream written = new ByteArrayOutputStream();
            private volatile boolean probeSent;

            @Override
            public void write(int b) {
                written.write(b);
            }

            @Override
            public void write(byte[] b, int off, int len) {
                written.write(b, off, len);
                probeSent = true;
            }

            @Override
            public void flush() {
                if (written.size() > 0) {
                    probeSent = true;
                }
            }
        }

        private final class ScriptedInputStream extends InputStream {
            private int pos = 0;
            private boolean inProbePhase = false;

            @Override
            public int read() {
                if (!inProbePhase) {
                    if (pos < initial.length) {
                        return initial[pos++] & 0xff;
                    }
                    inProbePhase = true;
                    pos = 0;
                }
                if (out.probeSent && pos < probeResponse.length) {
                    return probeResponse[pos++] & 0xff;
                }
                return -1;
            }

            @Override
            public int available() {
                if (!inProbePhase) {
                    return initial.length - pos;
                }
                if (out.probeSent) {
                    return probeResponse.length - pos;
                }
                return 0;
            }
        }
    }
}
