package com.gabriel.networkmonitor.repository;

import com.gabriel.networkmonitor.model.Device;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.List;

public class ScanRepository {

    private static final String URL = "jdbc:sqlite:network-monitor.db";

    public void inicializar() {
        String sql = """
            CREATE TABLE IF NOT EXISTS scan_resultado (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                ip TEXT NOT NULL,
                portas_abertas TEXT,
                data_hora TEXT NOT NULL
            )
            """;

        try (Connection conn = DriverManager.getConnection(URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao inicializar o banco", e);
        }
    }

    public void salvarScan(List<Device> dispositivos) {
        String sql = "INSERT INTO scan_resultado (ip, portas_abertas, data_hora) VALUES (?, ?, ?)";
        String agora = LocalDateTime.now().toString();

        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            for (Device device : dispositivos) {
                stmt.setString(1, device.getIp());
                stmt.setString(2, device.getPortasAbertas().toString());
                stmt.setString(3, agora);
                stmt.executeUpdate();
            }

        } catch (SQLException e) {
            throw new RuntimeException("Erro ao salvar scan", e);
        }
    }

    public void listarTodos() {
        String sql = "SELECT ip, portas_abertas, data_hora FROM scan_resultado ORDER BY id DESC";

        try (Connection conn = DriverManager.getConnection(URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                System.out.printf("[%s] %s -> %s%n",
                        rs.getString("data_hora"),
                        rs.getString("ip"),
                        rs.getString("portas_abertas"));
            }

        } catch (SQLException e) {
            throw new RuntimeException("Erro ao listar scans", e);
        }
    }

}
