package com.gabriel.networkmonitor.scanner;

import com.gabriel.networkmonitor.detector.ChangeDetector;
import com.gabriel.networkmonitor.model.Device;
import com.gabriel.networkmonitor.repository.ScanRepository;

import java.util.ArrayList;
import java.util.List;

public class NetworkScanner {

    private final DeviceScanner deviceScanner = new DeviceScanner();
    private final PortScanner portScanner = new PortScanner();

    public List<Device> scanCompleto(String subnet) throws InterruptedException {
        System.out.println("Etapa 1: procurando dispositivos ativos...");
        List<String> ipsAtivos = deviceScanner.scanRange(subnet);

        System.out.println("\nEtapa 2: verificando portas de cada dispositivo...");
        List<Device> dispositivos = new ArrayList<>();

        for (String ip : ipsAtivos) {
            List<Integer> portas = portScanner.scanIp(ip);
            dispositivos.add(new Device(ip, portas));
        }

        return dispositivos;
    }

    public static void main(String[] args) throws InterruptedException {
        NetworkScanner scanner = new NetworkScanner();

        // prefixo da rede
        String subnet = "192.168.1";

        List<Device> resultado = scanner.scanCompleto(subnet);

        System.out.println("\n=== Resultado final ===");
        resultado.forEach(System.out::println);

        ScanRepository repository = new ScanRepository();
        repository.inicializar();

        List<String> timestampsAntes = repository.buscarUltimosTimestamps();

        repository.salvarScan(resultado);
        System.out.println("\nResultado salvo no banco (network-monitor.db)");

        if (!timestampsAntes.isEmpty()) {
            String timestampAnterior = timestampsAntes.get(0);
            List<Device> scanAnterior = repository.buscarPorTimestamp(timestampAnterior);

            ChangeDetector detector = new ChangeDetector();
            List<String> mudancas = detector.detectarMudancas(scanAnterior, resultado);

            System.out.println("\n=== Mudanças detectadas desde o último scan ===");
            if (mudancas.isEmpty()) {
                System.out.println("Nenhuma mudança. Rede está como estava.");
            } else {
                mudancas.forEach(System.out::println);
            }
        } else {
            System.out.println("\nEste é o primeiro scan salvo, nada para comparar ainda.");
        }

        System.out.println("\n=== Histórico completo ===");
        repository.listarTodos();
    }

}
