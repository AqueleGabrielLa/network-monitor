package com.gabriel.networkmonitor.scanner;

import com.gabriel.networkmonitor.model.ArpEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ArpScanner {

    private static final Logger logger = LoggerFactory.getLogger(ArpScanner.class);

    private static final Path ARP_FILE = Path.of("/proc/net/arp");
    private static final String COMPLETE_FLAG = "0x2";
    private static final String EMPTY_MAC = "00:00:00:00:00:00";

    public List<ArpEntry> scan() throws IOException {
        List<String> lines = Files.readAllLines(ARP_FILE);

        return lines.stream()
                .skip(1)
                .map(ArpScanner::parse)
                .filter(entry -> entry != null)
                .toList();
    }

    private static ArpEntry parse(String line) {
        String[] parts = line.trim().split("\\s+");
        if (parts.length < 6) {
            return null;
        }

        String flag = parts[2];
        if (!COMPLETE_FLAG.equalsIgnoreCase(flag)) {
            return null;
        }

        String mac = normalizeMac(parts[3]);
        if (EMPTY_MAC.equals(mac)) {
            return null;
        }

        return new ArpEntry(parts[0], mac, parts[5]);
    }

    static String normalizeMac(String mac) {
        return mac.trim().toLowerCase().replace("-", ":");
    }
}