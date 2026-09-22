package com.gabriel.networkmonitor.detector;

import com.gabriel.networkmonitor.model.Device;

import java.util.*;

public class ChangeDetector {

    public List<String> detect(List<Device> previousScan, List<Device> currentScan) {
        List<String> events = new ArrayList<>();

        Map<String, Device> previousForMac = indexForMac(previousScan);
        Map<String, Device> currentForMac = indexForMac(currentScan);

        for (String mac : currentForMac.keySet()) {
            if (!previousForMac.containsKey(mac)) {
                events.add("[NOVO DISPOSITIVO] " + label(currentForMac.get(mac)) + " apareceu na rede");
            }
        }

        for (String mac : previousForMac.keySet()) {
            if (!currentForMac.containsKey(mac)) {
                events.add("[DISPOSITIVO SUMIU] " + label(previousForMac.get(mac)) + " não respondeu mais");
            }
        }

        for (String mac : currentForMac.keySet()) {
            if (previousForMac.containsKey(mac)) {
                Device before = previousForMac.get(mac);
                Device now = currentForMac.get(mac);

                if (!before.getIp().equals(now.getIp())) {
                    events.add("[IP MUDOU] " + before.getIp() + " -> " + now.getIp()
                            + " (MAC " + mac + ")");
                }

                events.addAll(comparePorts(before, now));
            }
        }

        return events;
    }

    private List<String> comparePorts(Device before, Device now) {
        List<String> events = new ArrayList<>();
        String deviceLabel = label(now);

        Set<Integer> portsBefore = new HashSet<>(before.getOpenPorts());
        Set<Integer> portsNow = new HashSet<>(now.getOpenPorts());

        for (Integer port : portsNow) {
            if (!portsBefore.contains(port)) {
                events.add("[PORTA ABRIU] " + deviceLabel + " abriu a porta " + port);
            }
        }

        for (Integer port : portsBefore) {
            if (!portsNow.contains(port)) {
                events.add("[PORTA FECHOU] " + deviceLabel + " fechou a porta " + port);
            }
        }

        return events;
    }

    private Map<String, Device> indexForMac(List<Device> devices) {
        Map<String, Device> map = new HashMap<>();
        for (Device d : devices) {
            map.put(d.getMac(), d);
        }
        return map;
    }

    private String label(Device d) {
        if (d.getMac().equals(d.getIp())) {
            return d.getMac();
        }
        return d.getMac() + " [" + d.getIp() + "]";
    }

}