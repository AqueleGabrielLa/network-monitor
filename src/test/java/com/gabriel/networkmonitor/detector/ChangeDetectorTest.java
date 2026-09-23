package com.gabriel.networkmonitor.detector;

import com.gabriel.networkmonitor.model.Device;
import com.gabriel.networkmonitor.model.PortInfo;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChangeDetectorTest {

    private final ChangeDetector detector = new ChangeDetector();

    private Device device(String mac, String ip, List<Integer> ports) {
        return new Device(mac, ip, null, null, ports);
    }

    @Test
    void shouldDetectNewDevice() {
        List<Device> previous = Collections.emptyList();
        List<Device> current = List.of(device("aa:bb:cc:00:00:01", "192.168.0.1", List.of(80)));

        List<String> changes = detector.detect(previous, current);

        assertEquals(1, changes.size());
        assertTrue(changes.get(0).contains("NOVO DISPOSITIVO"));
        assertTrue(changes.get(0).contains("aa:bb:cc:00:00:01"));
    }

    @Test
    void shouldDetectRemovedDevice() {
        List<Device> previous = List.of(device("aa:bb:cc:00:00:01", "192.168.0.1", List.of(80)));
        List<Device> current = Collections.emptyList();

        List<String> changes = detector.detect(previous, current);

        assertEquals(1, changes.size());
        assertTrue(changes.get(0).contains("DISPOSITIVO SUMIU"));
        assertTrue(changes.get(0).contains("aa:bb:cc:00:00:01"));
    }

    @Test
    void shouldDetectOpenedPort() {
        List<Device> previous = List.of(device("aa:bb:cc:00:00:01", "192.168.0.1", List.of(80)));
        List<Device> current = List.of(device("aa:bb:cc:00:00:01", "192.168.0.1", List.of(80, 443)));

        List<String> changes = detector.detect(previous, current);

        assertEquals(1, changes.size());
        assertTrue(changes.get(0).contains("PORTA ABRIU"));
        assertTrue(changes.get(0).contains("443"));
    }

    @Test
    void shouldDetectClosedPort() {
        List<Device> previous = List.of(device("aa:bb:cc:00:00:01", "192.168.0.1", List.of(80, 443)));
        List<Device> current = List.of(device("aa:bb:cc:00:00:01", "192.168.0.1", List.of(80)));

        List<String> changes = detector.detect(previous, current);

        assertEquals(1, changes.size());
        assertTrue(changes.get(0).contains("PORTA FECHOU"));
        assertTrue(changes.get(0).contains("443"));
    }

    @Test
    void shouldReturnEmptyWhenNoChanges() {
        List<Device> previous = List.of(device("aa:bb:cc:00:00:01", "192.168.0.1", List.of(80, 443)));
        List<Device> current = List.of(device("aa:bb:cc:00:00:01", "192.168.0.1", List.of(80, 443)));

        List<String> changes = detector.detect(previous, current);

        assertTrue(changes.isEmpty());
    }

    @Test
    void shouldHandleEmptyLists() {
        List<String> changes = detector.detect(Collections.emptyList(), Collections.emptyList());

        assertTrue(changes.isEmpty());
    }

    @Test
    void shouldNotReportNewDeviceWhenOnlyIpChanged() {
        List<Device> previous = List.of(device("aa:bb:cc:00:00:01", "192.168.0.1", List.of(80)));
        List<Device> current = List.of(device("aa:bb:cc:00:00:01", "192.168.0.77", List.of(80)));

        List<String> changes = detector.detect(previous, current);

        assertFalse(changes.stream().anyMatch(e -> e.contains("NOVO DISPOSITIVO")));
        assertFalse(changes.stream().anyMatch(e -> e.contains("DISPOSITIVO SUMIU")));
    }

    @Test
    void shouldReportIpChangeInformatively() {
        List<Device> previous = List.of(device("aa:bb:cc:00:00:01", "192.168.0.1", List.of(80)));
        List<Device> current = List.of(device("aa:bb:cc:00:00:01", "192.168.0.77", List.of(80)));

        List<String> changes = detector.detect(previous, current);

        assertEquals(1, changes.size());
        assertTrue(changes.get(0).contains("IP MUDOU"));
        assertTrue(changes.get(0).contains("192.168.0.1"));
        assertTrue(changes.get(0).contains("192.168.0.77"));
    }

    @Test
    void shouldDetectServiceChange() {
        Device previous = new Device("aa:bb:cc:00:00:01", "192.168.0.1", null, null,
                List.of(new PortInfo(80, "http Apache/2.4.0", null)), null);
        Device current = new Device("aa:bb:cc:00:00:01", "192.168.0.1", null, null,
                List.of(new PortInfo(80, "http nginx/1.25.3", null)), null);

        List<String> changes = detector.detect(List.of(previous), List.of(current));

        assertEquals(1, changes.size());
        assertTrue(changes.get(0).contains("SERVIÇO MUDOU"));
        assertTrue(changes.get(0).contains("Apache/2.4.0"));
        assertTrue(changes.get(0).contains("nginx/1.25.3"));
    }

    @Test
    void shouldNotDetectServiceChangeWhenServiceUnchanged() {
        Device previous = new Device("aa:bb:cc:00:00:01", "192.168.0.1", null, null,
                List.of(new PortInfo(80, "http nginx/1.25.3", null)), null);
        Device current = new Device("aa:bb:cc:00:00:01", "192.168.0.1", null, null,
                List.of(new PortInfo(80, "http nginx/1.25.3", null)), null);

        List<String> changes = detector.detect(List.of(previous), List.of(current));

        assertTrue(changes.isEmpty());
    }
}