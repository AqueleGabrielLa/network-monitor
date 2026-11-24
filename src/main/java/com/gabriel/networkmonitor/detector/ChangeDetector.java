package com.gabriel.networkmonitor.detector;

import com.gabriel.networkmonitor.banner.ServiceIdentifier;
import com.gabriel.networkmonitor.model.Device;
import com.gabriel.networkmonitor.model.PortInfo;

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
                events.addAll(compareServices(before, now));
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

    private List<String> compareServices(Device before, Device now) {
        List<String> events = new ArrayList<>();
        String deviceLabel = label(now);

        Map<Integer, PortInfo> portsBefore = portsByPort(before);
        Map<Integer, PortInfo> portsNow = portsByPort(now);

        for (Map.Entry<Integer, PortInfo> entry : portsNow.entrySet()) {
            PortInfo previous = portsBefore.get(entry.getKey());
            if (previous == null) {
                continue;
            }
            PortInfo current = entry.getValue();
            String previousLabel = fingerprint(previous);
            String currentLabel = fingerprint(current);
            if (!previousLabel.equals(currentLabel)) {
                events.add("[SERVIÇO MUDOU] " + deviceLabel + " porta " + entry.getKey()
                        + ": " + previousLabel + " -> " + currentLabel);
            }
        }

        return events;
    }

    private String fingerprint(PortInfo info) {
        String label = ServiceIdentifier.displayLabel(info.port(), info.banner(), info.service());
        if (label != null) {
            return label;
        }
        if (info.banner() != null && !info.banner().isBlank()) {
            String firstLine = info.banner().lines().findFirst().orElse(info.banner()).trim();
            return firstLine.length() > 60 ? firstLine.substring(0, 60) : firstLine;
        }
        return "unknown";
    }

    private Map<Integer, PortInfo> portsByPort(Device device) {
        Map<Integer, PortInfo> map = new HashMap<>();
        for (var info : device.getPortInfos()) {
            map.put(info.port(), info);
        }
        return map;
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