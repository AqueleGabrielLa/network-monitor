package com.gabriel.networkmonitor.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.DatagramSocket;
import java.net.InetAddress;

public class SubnetDetector {

    private static final Logger logger = LoggerFactory.getLogger(SubnetDetector.class);

    private static final String DNS_PROBE_HOST = "8.8.8.8";
    private static final int DNS_PROBE_PORT = 53;

    public String detect() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName(DNS_PROBE_HOST), DNS_PROBE_PORT);
            String localIp = socket.getLocalAddress().getHostAddress();
            return extractSubnet(localIp);
        } catch (Exception e) {
            logger.error("Falha ao detectar subnet automaticamente: {}", e.getMessage());
            return null;
        }
    }

    static String extractSubnet(String localIp) {
        if (localIp == null || localIp.isBlank()) {
            return null;
        }
        int lastDot = localIp.lastIndexOf('.');
        if (lastDot < 0) {
            return null;
        }
        return localIp.substring(0, lastDot);
    }
}