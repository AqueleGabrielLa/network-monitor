package com.gabriel.networkmonitor.detector;

import com.gabriel.networkmonitor.model.Device;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChangeDetectorTest {

    private final ChangeDetector detector = new ChangeDetector();

    @Test
    void shouldDetectNewDevice() {
        List<Device> previous = Collections.emptyList();
        List<Device> current = List.of(new Device("192.168.0.1", List.of(80)));

        List<String> changes = detector.detect(previous, current);

        assertEquals(1, changes.size());
        assertTrue(changes.get(0).contains("NOVO DISPOSITIVO"));
        assertTrue(changes.get(0).contains("192.168.0.1"));
    }

    @Test
    void shouldDetectRemovedDevice() {
        List<Device> previous = List.of(new Device("192.168.0.1", List.of(80)));
        List<Device> current = Collections.emptyList();

        List<String> changes = detector.detect(previous, current);

        assertEquals(1, changes.size());
        assertTrue(changes.get(0).contains("DISPOSITIVO SUMIU"));
        assertTrue(changes.get(0).contains("192.168.0.1"));
    }

    @Test
    void shouldDetectOpenedPort() {
        List<Device> previous = List.of(new Device("192.168.0.1", List.of(80)));
        List<Device> current = List.of(new Device("192.168.0.1", List.of(80, 443)));

        List<String> changes = detector.detect(previous, current);

        assertEquals(1, changes.size());
        assertTrue(changes.get(0).contains("PORTA ABRIU"));
        assertTrue(changes.get(0).contains("443"));
    }

    @Test
    void shouldDetectClosedPort() {
        List<Device> previous = List.of(new Device("192.168.0.1", List.of(80, 443)));
        List<Device> current = List.of(new Device("192.168.0.1", List.of(80)));

        List<String> changes = detector.detect(previous, current);

        assertEquals(1, changes.size());
        assertTrue(changes.get(0).contains("PORTA FECHOU"));
        assertTrue(changes.get(0).contains("443"));
    }

    @Test
    void shouldReturnEmptyWhenNoChanges() {
        List<Device> previous = List.of(new Device("192.168.0.1", List.of(80, 443)));
        List<Device> current = List.of(new Device("192.168.0.1", List.of(80, 443)));

        List<String> changes = detector.detect(previous, current);

        assertTrue(changes.isEmpty());
    }

    @Test
    void shouldHandleEmptyLists() {
        List<String> changes = detector.detect(Collections.emptyList(), Collections.emptyList());

        assertTrue(changes.isEmpty());
    }
}
