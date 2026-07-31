package com.gabriel.networkmonitor.scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.*;

public class DeviceScanner {

    private static final Logger logger = LoggerFactory.getLogger(DeviceScanner.class);

    // Timeout em milissegundos pra cada tentativa de ping
    private static final int TIMEOUT = 200;

    public List<String> scanRange(String subnet) throws InterruptedException {
        List<String> actives = new CopyOnWriteArrayList<>(); // thread-safe

        // pool de threads
        ExecutorService executor = Executors.newFixedThreadPool(50);

        for (int i = 1; i <= 254; i++) {
            String ip = subnet + "." + i;
            executor.submit(() -> {
                try {
                    InetAddress address = InetAddress.getByName(ip);
                    if (address.isReachable(TIMEOUT)) {
                        actives.add(ip);
                        logger.info("Dispositivo ativo encontrado: {}", ip);
                    }
                } catch (Exception e) {
                    logger.debug("Falha ao testar {}: {}", ip, e.getMessage());
                }
            });
        }

        executor.shutdown();
        boolean finished = executor.awaitTermination(30, TimeUnit.SECONDS);

        if(!finished){
            logger.warn("Scan de dispositivos não terminou dentro do prazo de 30s. " +
                    "A lista de dispositivos ativos pode estar incompleta.");
        }

        return actives;
    }

}