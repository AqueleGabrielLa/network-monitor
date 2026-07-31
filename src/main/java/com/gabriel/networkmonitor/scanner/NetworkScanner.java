package com.gabriel.networkmonitor.scanner;

import com.gabriel.networkmonitor.detector.ChangeDetector;
import com.gabriel.networkmonitor.model.Device;
import com.gabriel.networkmonitor.repository.ScanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class NetworkScanner {

    private static final Logger logger = LoggerFactory.getLogger(NetworkScanner.class);

    private final DeviceScanner deviceScanner = new DeviceScanner();
    private final PortScanner portScanner = new PortScanner();

    public List<Device> fullScan(String subnet) throws InterruptedException {
        logger.info("Etapa 1: procurando dispositivos ativos...");
        List<String> activeIps = deviceScanner.scanRange(subnet);

        logger.info("Etapa 2: verificando portas de cada dispositivo...");
        List<Device> devices = new ArrayList<>();

        for (String ip : activeIps) {
            List<Integer> ports = portScanner.scanIp(ip);
            devices.add(new Device(ip, ports));
        }

        return devices;
    }

    public static void main(String[] args) throws InterruptedException {

        if (args.length < 1) {
            System.out.println("Uso: java NetworkScanner <subnet>");
            System.out.println("Exemplo: java NetworkScanner 192.168.1");
            return;
        }

        NetworkScanner scanner = new NetworkScanner();

        String subnet = args[0];

        List<Device> result = scanner.fullScan(subnet);

        logger.info("=== Resultado final ===");
        result.forEach(device -> logger.info("{}", device));

        ScanRepository repository = new ScanRepository();
        repository.initialize();

        List<String> timestampsBefore = repository.searchLastTimestamps();

        repository.saveScan(result);
        logger.info("Resultado salvo no banco (network-monitor.db)");

        if (!timestampsBefore.isEmpty()) {
            String timestampPrevious = timestampsBefore.get(0);
            List<Device> scanPrevious = repository.searchByTimestamp(timestampPrevious);

            ChangeDetector detector = new ChangeDetector();
            List<String> changes = detector.detect(scanPrevious, result);

            logger.info("=== Mudanças detectadas desde o último scan ===");
            if (changes.isEmpty()) {
                logger.info("Nenhuma mudança. Rede está como estava.");
            } else {
                changes.forEach(change -> logger.info("{}", change));
            }
        } else {
            logger.info("Este é o primeiro scan salvo, nada para comparar ainda.");
        }

        logger.info("=== Histórico completo ===");
        repository.listAll();
    }

}
