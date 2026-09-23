package com.gabriel.networkmonitor.repository;

import com.gabriel.networkmonitor.config.AppConfig;
import com.gabriel.networkmonitor.model.Device;
import com.gabriel.networkmonitor.model.PortInfo;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ScanRepository {

    public static final Logger logger = LoggerFactory.getLogger(ScanRepository.class);

    private static final class DeviceAccumulator {
        final String mac;
        String ip;
        String hostname;
        String vendor;
        String osGuess;
        final List<PortInfo> portInfos = new ArrayList<>();

        DeviceAccumulator(String mac) {
            this.mac = mac;
        }
    }

    private final String url;

    public ScanRepository() {
        this.url = AppConfig.getString("database.url", "jdbc:sqlite:network-monitor.db");
    }

    public void initialize() {
        runMigration("/db/migration/V001__schema_normalized.sql");
        runMigration("/db/migration/V002__service_fingerprint.sql");
    }

    private void runMigration(String path) {
        String sql = readResource(path);

        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {
            for (String s : sql.split(";")) {
                String trimmed = s.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                try {
                    stmt.execute(trimmed);
                } catch (SQLException e) {
                    if (e.getMessage() != null && e.getMessage().contains("duplicate column name")) {
                        continue;
                    }
                    throw e;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao inicializar o banco", e);
        }
    }

    public int saveScan(List<Device> devices) {
        String insertScan = "INSERT INTO scan (executed_at, strategy) VALUES (?, ?)";
        String upsertDevice = """
            INSERT INTO device (mac, ip, hostname, vendor, os_guess, first_seen, last_seen)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(mac) DO UPDATE SET
                ip = excluded.ip,
                hostname = excluded.hostname,
                vendor = excluded.vendor,
                os_guess = excluded.os_guess,
                last_seen = excluded.last_seen
            """;
        String insertPort = "INSERT INTO scan_port (scan_id, device_id, port, banner, service) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DriverManager.getConnection(url)) {
            conn.setAutoCommit(false);

            String now = LocalDateTime.now().toString();

            int scanId;
            try (PreparedStatement stmt = conn.prepareStatement(insertScan, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, now);
                stmt.setString(2, "quick");
                stmt.executeUpdate();
                ResultSet keys = stmt.getGeneratedKeys();
                keys.next();
                scanId = keys.getInt(1);
            }

            try (PreparedStatement stmtDevice = conn.prepareStatement(upsertDevice);
                 PreparedStatement stmtPort = conn.prepareStatement(insertPort)) {

                for (Device device : devices) {
                    String mac = device.getMac();

                    stmtDevice.setString(1, mac);
                    stmtDevice.setString(2, device.getIp());
                    stmtDevice.setString(3, device.getHostname());
                    stmtDevice.setString(4, device.getVendor());
                    stmtDevice.setString(5, device.getOsGuess());
                    stmtDevice.setString(6, now);
                    stmtDevice.setString(7, now);
                    stmtDevice.executeUpdate();

                    if (device.getPortInfos().isEmpty()) {
                        stmtPort.setInt(1, scanId);
                        stmtPort.setString(2, mac);
                        stmtPort.setInt(3, 0);
                        stmtPort.setString(4, null);
                        stmtPort.setString(5, null);
                        stmtPort.executeUpdate();
                    } else {
                        for (PortInfo info : device.getPortInfos()) {
                            stmtPort.setInt(1, scanId);
                            stmtPort.setString(2, mac);
                            stmtPort.setInt(3, info.port());
                            stmtPort.setString(4, info.banner());
                            stmtPort.setString(5, info.service());
                            stmtPort.executeUpdate();
                        }
                    }
                }
            }

            conn.commit();
            return scanId;

        } catch (SQLException e) {
            throw new RuntimeException("Erro ao salvar scan", e);
        }
    }

    public List<Device> searchByScanId(int scanId) {
        String sql = """
            SELECT d.mac, d.ip, d.hostname, d.vendor, d.os_guess,
                   sp.port, sp.banner, sp.service
            FROM scan_port sp
            JOIN device d ON d.mac = sp.device_id
            WHERE sp.scan_id = ?
            """;
        Map<String, DeviceAccumulator> devices = new LinkedHashMap<>();

        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, scanId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String mac = rs.getString("mac");
                    DeviceAccumulator acc = devices.computeIfAbsent(mac, DeviceAccumulator::new);
                    acc.ip = rs.getString("ip");
                    acc.hostname = rs.getString("hostname");
                    acc.vendor = rs.getString("vendor");
                    acc.osGuess = rs.getString("os_guess");
                    int port = rs.getInt("port");
                    if (port != 0) {
                        acc.portInfos.add(new PortInfo(
                                port,
                                rs.getString("service"),
                                rs.getString("banner")));
                    }
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar devices por scan_id", e);
        }

        return devices.values().stream()
                .map(a -> new Device(a.mac, a.ip, a.hostname, a.vendor, a.portInfos, a.osGuess))
                .toList();
    }

    public int getLastScanId() {
        String sql = "SELECT id FROM scan ORDER BY id DESC LIMIT 1";

        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                return rs.getInt("id");
            }
            return -1;

        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar último scan_id", e);
        }
    }

    private String readResource(String path) {
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null) throw new RuntimeException("Resource not found: " + path);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Erro ao ler resource: " + path, e);
        }
    }

}
