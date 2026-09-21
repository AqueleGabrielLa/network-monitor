package com.gabriel.networkmonitor.scanner;

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

public class StableScanStrategy implements PortScanStrategy {

    private static final Logger logger = LoggerFactory.getLogger(StableScanStrategy.class);

    private static final int TIMEOUT_TERMINATION = 45;
    private static final int POOL_SIZE = 100;
    private static final int MAX_PORT = 32767;

    private final SocketFactory socketFactory;

    public StableScanStrategy(SocketFactory socketFactory) {
        this.socketFactory = socketFactory;
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

        for (int i = 1; i <= MAX_PORT; i++) {
            int port = i;
            executor.submit(() -> {
                if (testPort(ip, port)) {
                    openPorts.add(port);
                    logger.info("Porta aberta encontrada em {}: {}", ip, port);
                }
            });
        }

        executor.shutdown();
        boolean finished = executor.awaitTermination(TIMEOUT_TERMINATION, TimeUnit.SECONDS);

        if (!finished) {
            logger.warn("Scan de portas não terminou dentro do prazo de " + TIMEOUT_TERMINATION +
                    "s. A lista de portas ativas pode estar incompleta.");
        }

        Collections.sort(openPorts);
        return openPorts;
    }

}
