package com.gabriel.networkmonitor.scanner;

import com.gabriel.networkmonitor.config.AppConfig;
import com.gabriel.networkmonitor.interfaces.PortScanStrategy;
import com.gabriel.networkmonitor.interfaces.SocketFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class FullScanStrategy implements PortScanStrategy {

    private static final Logger logger = LoggerFactory.getLogger(FullScanStrategy.class);

    private static final int POOL_SIZE = 100;

    private final SocketFactory socketFactory;
    private final int timeoutTermination;

    public FullScanStrategy(SocketFactory socketFactory) {
        this.socketFactory = socketFactory;
        this.timeoutTermination = AppConfig.getInt("scanner.full-wait", 180);
    }

    public boolean testPort(String ip, int port) {
        try (var socket = socketFactory.create(ip, port)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public List<Integer> scanIp(String ip) throws InterruptedException {
        List<Integer> openPorts = new CopyOnWriteArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(POOL_SIZE);

        for (int i = 1; i <= 65535; i++) {
            int port = i;
            executor.submit(() -> {
                if (testPort(ip, port)) {
                    openPorts.add(port);
                    logger.info("Porta aberta encontrada em {}: {}", ip, port);
                }
            });

        }

        executor.shutdown();
        boolean finished = executor.awaitTermination(timeoutTermination, TimeUnit.SECONDS);

        if(!finished){
            logger.warn("Scan de portas não terminou dentro do prazo de " + timeoutTermination +
                    "s. A lista de portas ativas pode estar incompleta.");
        }

        Collections.sort(openPorts);
        return openPorts;
    }

}
