package com.gabriel.networkmonitor.model;

import java.util.List;

public class Device {

    private final String mac;
    private final String ip;
    private final String hostname;
    private final String vendor;
    private final List<Integer> openPorts;

    public Device(String mac, String ip, String hostname, String vendor, List<Integer> openPorts) {
        this.mac = mac;
        this.ip = ip;
        this.hostname = hostname;
        this.vendor = vendor;
        this.openPorts = openPorts;
    }

    public Device(String ip, List<Integer> openPorts) {
        this(ip, ip, null, null, openPorts);
    }

    public Device withPorts(List<Integer> ports) {
        return new Device(mac, ip, hostname, vendor, ports);
    }

    public String getMac() {
        return mac;
    }

    public String getIp() {
        return ip;
    }

    public String getHostname() {
        return hostname;
    }

    public String getVendor() {
        return vendor;
    }

    public List<Integer> getOpenPorts() {
        return openPorts;
    }

    public boolean hasOpenPorts() {
        return !openPorts.isEmpty();
    }

    @Override
    public String toString() {
        if (openPorts.isEmpty()) {
            return ip + " -> nenhuma porta comum aberta";
        }
        return ip + " -> portas abertas: " + openPorts;
    }
}