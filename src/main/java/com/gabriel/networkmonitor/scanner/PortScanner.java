package com.gabriel.networkmonitor.scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;

public class PortScanner {

    private static final Logger logger = LoggerFactory.getLogger(PortScanner.class);

    private static final int TIMEOUT = 150;

    private static final int[] COMMON_PORTS = {
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

    public boolean testPort(String ip, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), TIMEOUT);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public List<Integer> scanIp(String ip) throws InterruptedException {
        List<Integer> openPorts = new CopyOnWriteArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(COMMON_PORTS.length);

        for (int port : COMMON_PORTS) {
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
