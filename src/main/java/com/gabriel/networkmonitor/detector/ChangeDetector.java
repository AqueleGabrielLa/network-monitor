package com.gabriel.networkmonitor.detector;

import com.gabriel.networkmonitor.model.Device;

import java.util.*;

public class ChangeDetector {

    public List<String> detectarMudancas(List<Device> scanAnterior, List<Device> scanAtual) {
        List<String> eventos = new ArrayList<>();

        // Indexamos por IP pra facilitar a comparação (Map<IP, Device>)
        Map<String, Device> anteriorPorIp = indexarPorIp(scanAnterior);
        Map<String, Device> atualPorIp = indexarPorIp(scanAtual);

        // 1. Dispositivos que estão no scan atual mas não estavam no anterior -> NOVO
        for (String ip : atualPorIp.keySet()) {
            if (!anteriorPorIp.containsKey(ip)) {
                eventos.add("[NOVO DISPOSITIVO] " + ip + " apareceu na rede");
            }
        }

        // 2. Dispositivos que estavam no anterior mas não estão mais no atual -> SUMIU
        for (String ip : anteriorPorIp.keySet()) {
            if (!atualPorIp.containsKey(ip)) {
                eventos.add("[DISPOSITIVO SUMIU] " + ip + " não respondeu mais");
            }
        }

        // 3. Dispositivos presentes nos dois -> comparar as portas
        for (String ip : atualPorIp.keySet()) {
            if (anteriorPorIp.containsKey(ip)) {
                Device antes = anteriorPorIp.get(ip);
                Device agora = atualPorIp.get(ip);
                eventos.addAll(compararPortas(ip, antes, agora));
            }
        }

        return eventos;
    }

    private List<String> compararPortas(String ip, Device antes, Device agora) {
        List<String> eventos = new ArrayList<>();

        Set<Integer> portasAntes = new HashSet<>(antes.getPortasAbertas());
        Set<Integer> portasAgora = new HashSet<>(agora.getPortasAbertas());

        for (Integer porta : portasAgora) {
            if (!portasAntes.contains(porta)) {
                eventos.add("[PORTA ABRIU] " + ip + " abriu a porta " + porta);
            }
        }

        for (Integer porta : portasAntes) {
            if (!portasAgora.contains(porta)) {
                eventos.add("[PORTA FECHOU] " + ip + " fechou a porta " + porta);
            }
        }

        return eventos;
    }

    private Map<String, Device> indexarPorIp(List<Device> devices) {
        Map<String, Device> mapa = new HashMap<>();
        for (Device d : devices) {
            mapa.put(d.getIp(), d);
        }
        return mapa;
    }

}
