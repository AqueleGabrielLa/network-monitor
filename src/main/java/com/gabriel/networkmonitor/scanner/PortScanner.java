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

    public boolean testarPorta(String ip, int porta) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, porta), TIMEOUT);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

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

}
