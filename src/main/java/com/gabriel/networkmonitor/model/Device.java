package com.gabriel.networkmonitor.model;

import java.util.List;

/**
 * Representa um dispositivo encontrado na rede,
 * junto com as portas que estão abertas nele.
 */
public class Device {

    private final String ip;
    private final List<Integer> portasAbertas;

    public Device(String ip, List<Integer> portasAbertas) {
        this.ip = ip;
        this.portasAbertas = portasAbertas;
    }

    public String getIp() {
        return ip;
    }

    public List<Integer> getPortasAbertas() {
        return portasAbertas;
    }

    public boolean temPortasAbertas(){
        return !portasAbertas.isEmpty();
    }

    @Override
    public String toString() {
        if(portasAbertas.isEmpty()){
            return ip + " -> nenhuma porta comum aberta";
        }
        return ip + " -> portas abertas: " + portasAbertas;
    }
}
