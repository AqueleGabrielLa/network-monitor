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
    private static final Pattern VSFTP = Pattern.compile("^220\\s+.*vsFTPd\\s*(.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern FTP = Pattern.compile("^220\\s+.*\\bFTP\\b.*$", Pattern.CASE_INSENSITIVE);

    public Optional<String> identify(int port, String banner) {
        if (banner != null && !banner.isBlank()) {
            Optional<String> fromBanner = parseBanner(banner);
            if (fromBanner.isPresent()) {
                return fromBanner;
            }
        }
        return Optional.ofNullable(WELL_KNOWN_PORTS.get(port));
    }

    private Optional<String> parseBanner(String banner) {
        Matcher ssh = SSH.matcher(banner.trim());
        if (ssh.matches()) {
            String version = ssh.group(2).trim();
            return Optional.of(version.isEmpty() ? "ssh" : "ssh " + version);
        }

        if (HTTP_STATUS.matcher(banner.trim()).find()) {
            Matcher server = HTTP_SERVER.matcher(banner);
            if (server.find()) {
                return Optional.of("http " + server.group(1).trim());
            }
            return Optional.of("http");
        }

        Matcher smtp = SMTP.matcher(banner.trim());
        if (smtp.matches()) {
            String extra = smtp.group(1) != null ? smtp.group(1).trim() : "";
            return Optional.of(extra.isEmpty() ? "smtp" : "smtp" + extra);
        }

        Matcher vsftp = VSFTP.matcher(banner.trim());
        if (vsftp.matches()) {
            String version = vsftp.group(1) != null ? vsftp.group(1).trim() : "";
            return Optional.of(version.isEmpty() ? "ftp vsFTPd" : "ftp vsFTPd " + version);
        }

        if (FTP.matcher(banner.trim()).matches()) {
            return Optional.of("ftp");
        }

        if (banner.contains("MySQL") || banner.contains("mysql")) {
            return Optional.of("mysql");
        }
        if (banner.contains("PostgreSQL")) {
            return Optional.of("postgresql");
        }
        if (banner.startsWith("-ERR") || banner.contains("redis")) {
            return Optional.of("redis");
        }

        return Optional.empty();
    }
}
