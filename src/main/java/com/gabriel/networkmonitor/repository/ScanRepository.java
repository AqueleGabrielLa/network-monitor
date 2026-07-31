package com.gabriel.networkmonitor.repository;

import com.gabriel.networkmonitor.model.Device;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.List;

public class ScanRepository {

    public static final Logger logger = LoggerFactory.getLogger(ScanRepository.class);

    private static final String URL = "jdbc:sqlite:network-monitor.db";

    public void initialize() {
        String sql = """
            CREATE TABLE IF NOT EXISTS scan_result (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                ip TEXT NOT NULL,
                open_ports TEXT,
                date_hour TEXT NOT NULL
            )
            """;

        try (Connection conn = DriverManager.getConnection(URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao inicializar o banco", e);
        }
    }

    public void saveScan(List<Device> devices) {
        String sql = "INSERT INTO scan_result (ip, open_ports, date_hour) VALUES (?, ?, ?)";
        String now = LocalDateTime.now().toString();

        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            for (Device device : devices) {
                stmt.setString(1, device.getIp());
                stmt.setString(2, joinPorts(device.getOpenPorts()));
                stmt.setString(3, now);
                stmt.executeUpdate();
            }

        } catch (SQLException e) {
            throw new RuntimeException("Erro ao salvar scan", e);
        }
    }

    public void listAll() {
        String sql = "SELECT ip, open_ports, date_hour FROM scan_result ORDER BY id DESC";

        try (Connection conn = DriverManager.getConnection(URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                logger.info("[{}] {} -> {}",
                        rs.getString("date_hour"),
                        rs.getString("ip"),
                        rs.getString("open_ports"));
            }

        } catch (SQLException e) {
            throw new RuntimeException("Erro ao listar scans", e);
        }
    }

    public List<String> searchLastTimestamps() {
        String sql = "SELECT DISTINCT date_hour FROM scan_result ORDER BY date_hour DESC LIMIT 2";
        List<String> timestamps = new java.util.ArrayList<>();

        try (Connection conn = DriverManager.getConnection(URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                timestamps.add(rs.getString("date_hour"));
            }

        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar timestamps", e);
        }
        return timestamps;
    }

        public List<Device> searchByTimestamp(String timestamp) {
        String sql = "SELECT ip, open_ports FROM scan_result WHERE date_hour = ?";
        List<Device> devices = new java.util.ArrayList<>();

        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, timestamp);

            try (ResultSet rs = stmt.executeQuery()){
                while (rs.next()) {
                    String ip = rs.getString("ip");
                    String portsText = rs.getString("open_ports");
                    List<Integer> ports = parsePorts(portsText);
                    devices.add(new Device(ip, ports));
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar devices por timestamp", e);
        }
        return devices;
    }

    private List<Integer> parsePorts(String text) {
        List<Integer> ports = new java.util.ArrayList<>();
        if (text == null || text.isBlank()) return ports;

        for (String part : text.split(",")) {
            ports.add(Integer.parseInt(part.trim()));
        }
        return ports;
    }

    private String joinPorts(List<Integer> ports) {
        List<String> textos = new java.util.ArrayList<>();
        for (Integer port : ports) {
            textos.add(String.valueOf(port));
        }
        return String.join(",", textos);
    }

}
