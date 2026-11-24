package com.gabriel.networkmonitor.banner;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ServiceIdentifier {

    private static final Map<Integer, String> WELL_KNOWN_PORTS = Map.ofEntries(
            Map.entry(21, "ftp"),
            Map.entry(22, "ssh"),
            Map.entry(23, "telnet"),
            Map.entry(25, "smtp"),
            Map.entry(53, "dns"),
            Map.entry(80, "http"),
            Map.entry(110, "pop3"),
            Map.entry(143, "imap"),
            Map.entry(443, "https"),
            Map.entry(445, "smb"),
            Map.entry(587, "smtp"),
            Map.entry(993, "imaps"),
            Map.entry(995, "pop3s"),
            Map.entry(3306, "mysql"),
            Map.entry(3389, "rdp"),
            Map.entry(5432, "postgresql"),
            Map.entry(6379, "redis"),
            Map.entry(8000, "http"),
            Map.entry(8080, "http"),
            Map.entry(8443, "https"),
            Map.entry(8888, "http"),
            Map.entry(9100, "printer")
    );

    private static final Pattern SSH = Pattern.compile("^SSH-([\\d.]+)-(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTTP_STATUS = Pattern.compile("^HTTP/\\d\\.\\d\\s+\\d{3}");
    private static final Pattern HTTP_SERVER = Pattern.compile("(?i)Server:\\s*([^\\r\\n]+)");
    private static final Pattern SMTP = Pattern.compile("^220\\s+.*ESMTP(.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern VSFTP = Pattern.compile("^220\\s+\\(?vsFTPd\\s*([^)]*)\\)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern FTP = Pattern.compile("^220\\s+.*\\bFTP\\b.*$", Pattern.CASE_INSENSITIVE);

    public Optional<String> identify(int port, String banner) {
        if (banner != null && !banner.isBlank()) {
            Optional<String> fromBanner = parseService(banner);
            if (fromBanner.isPresent()) {
                return fromBanner;
            }
        }
        return Optional.ofNullable(WELL_KNOWN_PORTS.get(port));
    }

    public static String versionFrom(String banner) {
        if (banner == null || banner.isBlank()) {
            return null;
        }

        String firstLine = banner.lines().findFirst().orElse(banner).trim();

        Matcher ssh = SSH.matcher(firstLine);
        if (ssh.matches()) {
            String version = ssh.group(2).trim();
            return version.isEmpty() ? null : version;
        }

        Matcher server = HTTP_SERVER.matcher(banner);
        if (server.find()) {
            String token = server.group(1).trim();
            return token.isEmpty() ? null : token;
        }

        Matcher smtp = SMTP.matcher(firstLine);
        if (smtp.matches()) {
            String extra = smtp.group(1) != null ? smtp.group(1).trim() : "";
            return extra.isEmpty() ? null : extra;
        }

        Matcher vsftp = VSFTP.matcher(firstLine);
        if (vsftp.matches()) {
            String version = vsftp.group(1) != null ? vsftp.group(1).trim() : "";
            if (version.isEmpty()) {
                return "vsFTPd";
            }
            return "vsFTPd " + version;
        }

        return null;
    }

    public static String displayLabel(int port, String banner, String service) {
        String resolved = service;
        if (resolved == null || resolved.isBlank()) {
            resolved = new ServiceIdentifier().identify(port, banner).orElse(null);
        }
        if (resolved == null) {
            return null;
        }
        String version = versionFrom(banner);
        if (version == null || version.isBlank()) {
            return resolved;
        }
        if (version.equalsIgnoreCase(resolved) || resolved.contains(version)) {
            return resolved;
        }
        return resolved + " " + version;
    }

    private Optional<String> parseService(String banner) {
        String firstLine = banner.lines().findFirst().orElse(banner).trim();

        if (SSH.matcher(firstLine).matches()) {
            return Optional.of("ssh");
        }

        if (HTTP_STATUS.matcher(firstLine).find()) {
            return Optional.of("http");
        }

        if (SMTP.matcher(firstLine).matches()) {
            return Optional.of("smtp");
        }

        if (VSFTP.matcher(firstLine).matches() || FTP.matcher(firstLine).matches()) {
            return Optional.of("ftp");
        }

        if (banner.contains("MySQL") || banner.contains("mysql")) {
            return Optional.of("mysql");
        }
        if (banner.contains("PostgreSQL")) {
            return Optional.of("postgresql");
        }
        if (firstLine.startsWith("-ERR") || banner.contains("redis")) {
            return Optional.of("redis");
        }

        return Optional.empty();
    }
}
