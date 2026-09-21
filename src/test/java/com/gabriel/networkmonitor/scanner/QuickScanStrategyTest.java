package com.gabriel.networkmonitor.scanner;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class QuickScanStrategyTest {

    @Test
    void shouldReturnOpenPorts() throws InterruptedException {
        SocketFake socketFactory = new SocketFake(Set.of(80, 443));
        QuickScanStrategy strategy = new QuickScanStrategy(socketFactory);

        List<Integer> ports = strategy.scanIp("192.168.0.1");

        assertTrue(ports.contains(80));
        assertTrue(ports.contains(443));
    }

    @Test
    void shouldReturnEmptyListWhenAllClosed() throws InterruptedException {
        SocketFake socketFactory = new SocketFake(Set.of());
        QuickScanStrategy strategy = new QuickScanStrategy(socketFactory);

        List<Integer> ports = strategy.scanIp("192.168.0.1");

        assertTrue(ports.isEmpty());
    }

    @Test
    void shouldHandleConnectionTimeout() throws InterruptedException {
        SocketFake socketFactory = new SocketFake(Set.of());
        QuickScanStrategy strategy = new QuickScanStrategy(socketFactory);

        List<Integer> ports = strategy.scanIp("192.168.0.1");

        assertNotNull(ports);
        assertTrue(ports.isEmpty());
    }
}
