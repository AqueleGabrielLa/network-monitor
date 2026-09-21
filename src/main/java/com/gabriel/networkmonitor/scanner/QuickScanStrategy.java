package com.gabriel.networkmonitor.scanner;

import com.gabriel.networkmonitor.config.AppConfig;
import com.gabriel.networkmonitor.interfaces.PortScanStrategy;
import com.gabriel.networkmonitor.interfaces.SocketFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;

public class QuickScanStrategy implements PortScanStrategy {

    private static final Logger logger = LoggerFactory.getLogger(QuickScanStrategy.class);

    private final SocketFactory socketFactory;
    private final int[] commonPorts;

    public QuickScanStrategy(SocketFactory socketFactory) {
        this.socketFactory = socketFactory;
        this.commonPorts = AppConfig.getIntArray("scanner.common-ports",
                new int[]{22, 80, 443, 445, 3389, 5353, 8080, 8888, 9100, 8000});
    }

    public boolean testPort(String ip, int port) {
        try (Socket socket = socketFactory.create(ip, port)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public List<Integer> scanIp(String ip) throws InterruptedException {
        List<Integer> openPorts = new CopyOnWriteArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(commonPorts.length);

        for (int port : commonPorts) {
            executor.submit(() -> {
                if (testPort(ip, port)) {
                    openPorts.add(port);
                }
            });
        }

        executor.shutdown();
        boolean finished = executor.awaitTermination(10, TimeUnit.SECONDS);

        if(!finished){
            logger.warn("Scan de portas não terminou dentro do prazo de 10s. " +
                    "A lista de portas ativas pode estar incompleta.");
        }

        Collections.sort(openPorts);
        return openPorts;
    }
}
