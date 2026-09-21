package com.gabriel.networkmonitor.scanner;

import com.gabriel.networkmonitor.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.*;

public class DeviceScanner {

    private static final Logger logger = LoggerFactory.getLogger(DeviceScanner.class);

    private final int timeout;
    private final int poolSize;

    public DeviceScanner() {
        this.timeout = AppConfig.getInt("scanner.timeout", 200);
        this.poolSize = AppConfig.getInt("scanner.pool-size", 50);
    }

    public List<String> scanRange(String subnet) throws InterruptedException {
        List<String> actives = new CopyOnWriteArrayList<>();

        ExecutorService executor = Executors.newFixedThreadPool(poolSize);

        for (int i = 1; i <= 254; i++) {
            String ip = subnet + "." + i;
            executor.submit(() -> {
                try {
                    InetAddress address = InetAddress.getByName(ip);
                    if (address.isReachable(timeout)) {
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
