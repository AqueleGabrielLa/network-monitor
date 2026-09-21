package com.gabriel.networkmonitor;

import com.gabriel.networkmonitor.detector.ChangeDetector;
import com.gabriel.networkmonitor.interfaces.PortScanStrategy;
import com.gabriel.networkmonitor.model.Device;
import com.gabriel.networkmonitor.repository.ScanRepository;
import com.gabriel.networkmonitor.scanner.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ScannerService {

    private static final Logger logger = LoggerFactory.getLogger(ScannerService.class);

    private final DeviceScanner deviceScanner;
    private final PortScanStrategy portScanner;
    private final ScanRepository repository;
    private final ChangeDetector detector;

    public ScannerService(PortScanStrategy portScanner) {
        this.deviceScanner = new DeviceScanner();
        this.portScanner = portScanner;
        this.repository = new ScanRepository();
        this.detector = new ChangeDetector();
    }

    public List<String> executeFullCycle(String subnet) throws InterruptedException {
        repository.initialize();

        List<Device> previousScan = null;
        int lastScanId = repository.getLastScanId();
        if (lastScanId != -1) {
            previousScan = repository.searchByScanId(lastScanId);
        }

        logger.info("Etapa 1: procurando dispositivos ativos...");
        List<String> activeIps = deviceScanner.scanRange(subnet);

        logger.info("Etapa 2: verificando portas de cada dispositivo...");
        var networkScanner = new NetworkScanner(portScanner);
        List<Device> result = networkScanner.fullScan(activeIps);

        repository.saveScan(result);
        logger.info("Resultado salvo no banco");

        if (previousScan != null) {
            List<String> changes = detector.detect(previousScan, result);

            logger.info("=== Mudanças detectadas desde o último scan ===");
            if (changes.isEmpty()) {
                logger.info("Nenhuma mudança. Rede está como estava.");
            } else {
                changes.forEach(change -> logger.info("{}", change));
            }
            return changes;
        } else {
            logger.info("Este é o primeiro scan salvo, nada para comparar ainda.");
            return List.of();
        }
    }

    public static PortScanStrategy createStrategy(String mode) {
        int timeout = com.gabriel.networkmonitor.config.AppConfig.getInt("scanner.timeout", 150);
        var socketFactory = new RealSocketFactory(timeout);

        return switch (mode) {
            case "--quick" -> new QuickScanStrategy(socketFactory);
            case "--full" -> new FullScanStrategy(socketFactory);
            case "--stable" -> new StableScanStrategy(socketFactory);
            default -> null;
        };
    }
}
