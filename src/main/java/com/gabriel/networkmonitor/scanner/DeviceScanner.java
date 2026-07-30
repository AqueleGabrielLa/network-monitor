package com.gabriel.networkmonitor.scanner;

import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.*;

public class DeviceScanner {

    // Timeout em milissegundos pra cada tentativa de ping
    private static final int TIMEOUT = 200;

    public List<String> scanRange(String subnet) throws InterruptedException {
        List<String> ativos = new CopyOnWriteArrayList<>(); // thread-safe

        // pool de threads
        ExecutorService executor = Executors.newFixedThreadPool(50);

        for (int i = 1; i <= 254; i++) {
            String ip = subnet + "." + i;
            executor.submit(() -> {
                try {
                    InetAddress endereco = InetAddress.getByName(ip);
                    if (endereco.isReachable(TIMEOUT)) {
                        ativos.add(ip);
                        System.out.println("Ativo: " + ip);
                    }
                } catch (Exception e) {

                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        return ativos;
    }

}