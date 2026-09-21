package com.gabriel.networkmonitor.repository;

import com.gabriel.networkmonitor.config.AppConfig;
import com.gabriel.networkmonitor.model.Device;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.List;

public class ScanRepository {

    public static final Logger logger = LoggerFactory.getLogger(ScanRepository.class);

    private final String url;

    public ScanRepository() {
        this.url = AppConfig.getString("database.url", "jdbc:sqlite:network-monitor.db");
    }

    public void initialize() {
        String sql = readResource("/db/migration/V001__schema_normalized.sql");

        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao inicializar o banco", e);
        }
    }

    public int saveScan(List<Device> devices) {
        String insertScan = "INSERT INTO scan (executed_at, strategy) VALUES (?, ?)";
        String insertDevice = """
            INSERT OR IGNORE INTO device (mac, ip, first_seen, last_seen)
            VALUES (?, ?, ?, ?)
            """;
        String insertPort = "INSERT INTO scan_port (scan_id, device_id, port) VALUES (?, ?, ?)";

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

            try (PreparedStatement stmtDevice = conn.prepareStatement(insertDevice);
                 PreparedStatement stmtPort = conn.prepareStatement(insertPort)) {

                for (Device device : devices) {
                    String mac = device.getIp();

                    stmtDevice.setString(1, mac);
                    stmtDevice.setString(2, device.getIp());
                    stmtDevice.setString(3, now);
                    stmtDevice.setString(4, now);
                    stmtDevice.executeUpdate();

                    for (Integer port : device.getOpenPorts()) {
                        stmtPort.setInt(1, scanId);
                        stmtPort.setString(2, mac);
                        stmtPort.setInt(3, port);
                        stmtPort.executeUpdate();
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
            SELECT d.ip, sp.port
            FROM scan_port sp
            JOIN device d ON d.mac = sp.device_id
            WHERE sp.scan_id = ?
            """;
        java.util.Map<String, java.util.List<Integer>> devicePorts = new java.util.HashMap<>();

        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, scanId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String ip = rs.getString("ip");
                    int port = rs.getInt("port");
                    devicePorts.computeIfAbsent(ip, k -> new java.util.ArrayList<>()).add(port);
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar devices por scan_id", e);
        }

        return devicePorts.entrySet().stream()
                .map(e -> new Device(e.getKey(), e.getValue()))
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
