package com.gabriel.networkmonitor.detector;

import com.gabriel.networkmonitor.model.Device;

import java.util.*;

public class ChangeDetector {

    public List<String> detect(List<Device> previousScan, List<Device> currentScan) {
        List<String> events = new ArrayList<>();

        Map<String, Device> previousForIp = indexForIp(previousScan);
        Map<String, Device> currentForIp = indexForIp(currentScan);

        for (String ip : currentForIp.keySet()) {
            if (!previousForIp.containsKey(ip)) {
                events.add("[NOVO DISPOSITIVO] " + ip + " apareceu na rede");
            }
        }

        for (String ip : previousForIp.keySet()) {
            if (!currentForIp.containsKey(ip)) {
                events.add("[DISPOSITIVO SUMIU] " + ip + " não respondeu mais");
            }
        }

        for (String ip : currentForIp.keySet()) {
            if (previousForIp.containsKey(ip)) {
                Device before = previousForIp.get(ip);
                Device now = currentForIp.get(ip);
                events.addAll(comparePorts(ip, before, now));
            }
        }

        return events;
    }

    private List<String> comparePorts(String ip, Device before, Device now) {
        List<String> events = new ArrayList<>();

        Set<Integer> portsBefore = new HashSet<>(before.getOpenPorts());
        Set<Integer> portsNow = new HashSet<>(now.getOpenPorts());

        for (Integer port : portsNow) {
            if (!portsBefore.contains(port)) {
                events.add("[PORTA ABRIU] " + ip + " abriu a porta " + port);
            }
        }

        for (Integer port : portsBefore) {
            if (!portsNow.contains(port)) {
                events.add("[PORTA FECHOU] " + ip + " fechou a porta " + port);
            }
        }

        return events;
    }

    private Map<String, Device> indexForIp(List<Device> devices) {
        Map<String, Device> map = new HashMap<>();
        for (Device d : devices) {
            map.put(d.getIp(), d);
        }
        return map;
    }

}
