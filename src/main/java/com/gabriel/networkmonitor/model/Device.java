package com.gabriel.networkmonitor.model;

import java.util.List;

public class Device {

    private final String mac;
    private final String ip;
    private final String hostname;
    private final String vendor;
    private final List<PortInfo> portInfos;
    private final String osGuess;

    public Device(String mac, String ip, String hostname, String vendor, List<Integer> openPorts) {
        this(mac, ip, hostname, vendor,
                openPorts.stream().map(p -> new PortInfo(p, null, null)).toList(),
                null);
    }

    public Device(String mac, String ip, String hostname, String vendor,
                  List<PortInfo> portInfos, String osGuess) {
        this.mac = mac;
        this.ip = ip;
        this.hostname = hostname;
        this.vendor = vendor;
        this.portInfos = portInfos;
        this.osGuess = osGuess;
    }

    public Device(String ip, List<Integer> openPorts) {
        this(ip, ip, null, null, openPorts);
    }

    public Device withPorts(List<Integer> ports) {
        return new Device(mac, ip, hostname, vendor,
                ports.stream().map(p -> new PortInfo(p, null, null)).toList(),
                osGuess);
    }

    public Device withPortInfos(List<PortInfo> infos) {
        return new Device(mac, ip, hostname, vendor, infos, osGuess);
    }

    public Device withOsGuess(String guess) {
        return new Device(mac, ip, hostname, vendor, portInfos, guess);
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

    public List<PortInfo> getPortInfos() {
        return portInfos;
    }

    public String getOsGuess() {
        return osGuess;
    }

    public List<Integer> getOpenPorts() {
        return portInfos.stream().map(PortInfo::port).toList();
    }

    public boolean hasOpenPorts() {
        return !portInfos.isEmpty();
    }

    @Override
    public String toString() {
        if (portInfos.isEmpty()) {
            return ip + " -> nenhuma porta comum aberta";
        }
        return ip + " -> portas abertas: " + getOpenPorts();
    }
}
