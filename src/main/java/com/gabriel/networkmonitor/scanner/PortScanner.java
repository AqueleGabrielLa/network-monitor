package com.gabriel.networkmonitor.scanner;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;

public class PortScanner {

    private static final int TIMEOUT = 150;

    private static final int[] PORTAS_COMUNS = {
            22,    // SSH
            80,    // HTTP
            443,   // HTTPS
            445,
            3389,
            5353,
            8080,
            8888,
            9100,
            8000
    };

    /**
     * Testa se uma porta específica está aberta em um IP.
     * Usamos conexão TCP direta em vez de ping.
     */
    public boolean testarPorta(String ip, int porta) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, porta), TIMEOUT);
            return true; // conectou = porta aberta
        } catch (Exception e) {
            return false; // recusou ou deu timeout = porta fechada/filtrada
        }
    }

    /**
     * Escaneia todas as portas comuns em um único IP.
     * Retorna a lista de portas que responderam.
     */
    public List<Integer> scanIp(String ip) throws InterruptedException {
        List<Integer> portasAbertas = new CopyOnWriteArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(PORTAS_COMUNS.length);

        for (int porta : PORTAS_COMUNS) {
            executor.submit(() -> {
                if (testarPorta(ip, porta)) {
                    portasAbertas.add(porta);
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        Collections.sort(portasAbertas);
        return portasAbertas;
    }

    public static void main(String[] args) throws InterruptedException {
        PortScanner scanner = new PortScanner();

        // Testa nos IPs que o DeviceScanner encontrou
        List<String> ipsParaTestar = List.of("192.168.1.1", "192.168.1.7");

        for (String ip : ipsParaTestar) {
            System.out.println("\nEscaneando portas de " + ip + " ...");
            List<Integer> abertas = scanner.scanIp(ip);

            if (abertas.isEmpty()) {
                System.out.println("  Nenhuma porta comum encontrada aberta.");
            } else {
                System.out.println("  Portas abertas: " + abertas);
            }
        }
    }

}
