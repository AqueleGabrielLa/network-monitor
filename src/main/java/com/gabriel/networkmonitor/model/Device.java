package com.gabriel.networkmonitor.model;

import java.util.List;

public class Device {

    private final String ip;
    private final List<Integer> openPorts;

    public Device(String ip, List<Integer> openPorts) {
        this.ip = ip;
        this.openPorts = openPorts;
    }

    public String getIp() {
        return ip;
    }

    public List<Integer> getOpenPorts() {
        return openPorts;
    }

    public boolean hasOpenPorts(){
        return !openPorts.isEmpty();
    }

    @Override
    public String toString() {
        if(openPorts.isEmpty()){
            return ip + " -> nenhuma porta comum aberta";
        }
        return ip + " -> portas abertas: " + openPorts;
    }
}
