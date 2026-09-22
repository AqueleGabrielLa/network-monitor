package com.gabriel.networkmonitor.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class OuiLookup {

    private static final Logger logger = LoggerFactory.getLogger(OuiLookup.class);

    private static final String OUI_FILE = "/oui.csv";

    private final Map<String, String> vendors = new HashMap<>();

    public OuiLookup() {
        load();
    }

    private void load() {
        try (InputStream is = OuiLookup.class.getResourceAsStream(OUI_FILE)) {
            if (is == null) {
                throw new IllegalStateException("Resource not found: " + OUI_FILE);
            }
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            for (String line : content.split("\n")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("oui,")) {
                    continue;
                }
                String[] parts = trimmed.split(",", 2);
                if (parts.length == 2) {
                    vendors.put(parts[0].trim().toLowerCase(), parts[1].trim());
                }
            }
            logger.info("Carregados {} prefixos OUI", vendors.size());
        } catch (IOException e) {
            throw new RuntimeException("Erro ao carregar " + OUI_FILE, e);
        }
    }

    public String lookup(String mac) {
        if (mac == null || mac.length() < 8) {
            return null;
        }
        String oui = mac.substring(0, 8).toLowerCase();
        return vendors.get(oui);
    }
}