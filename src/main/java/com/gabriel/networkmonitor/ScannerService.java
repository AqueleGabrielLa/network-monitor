package com.gabriel.networkmonitor;

import com.gabriel.networkmonitor.banner.BannerGrabber;
import com.gabriel.networkmonitor.banner.ServiceIdentifier;
import com.gabriel.networkmonitor.config.AppConfig;
import com.gabriel.networkmonitor.detector.ChangeDetector;
import com.gabriel.networkmonitor.interfaces.PortScanStrategy;
import com.gabriel.networkmonitor.model.Device;
import com.gabriel.networkmonitor.model.PortInfo;
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
    private final BannerGrabber bannerGrabber;
    private final ServiceIdentifier serviceIdentifier;

    public ScannerService(PortScanStrategy portScanner) {
        this.deviceScanner = new DeviceScanner();
        this.portScanner = portScanner;
        this.repository = new ScanRepository();
        this.detector = new ChangeDetector();
        int timeout = AppConfig.getInt("scanner.timeout", 150);
        this.bannerGrabber = new BannerGrabber(new RealSocketFactory(timeout));
        this.serviceIdentifier = new ServiceIdentifier();
    }

    public List<String> executeFullCycle(String subnet) throws InterruptedException {
        repository.initialize();

        List<Device> previousScan = null;
        int lastScanId = repository.getLastScanId();
        if (lastScanId != -1) {
            previousScan = repository.searchByScanId(lastScanId);
        }

        logger.info("Etapa 1: procurando dispositivos ativos...");
        List<Device> activeDevices = deviceScanner.scanRange(subnet);

        logger.info("Etapa 2: verificando portas de cada dispositivo...");
        var networkScanner = new NetworkScanner(portScanner);
        List<Device> scanned = networkScanner.fullScan(activeDevices);

        logger.info("Etapa 3: coletando banners e identificando serviços...");
        List<Device> result = enrichWithFingerprints(scanned);

        repository.saveScan(result);
        logger.info("Resultado salvo no banco");

        logger.info("=== Resultado final ===");
        result.forEach(device -> logger.info("{}", describe(device)));

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

    private List<Device> enrichWithFingerprints(List<Device> devices) {
        return devices.stream()
                .map(device -> device.withPortInfos(
                        device.getPortInfos().stream()
                                .map(info -> enrichPort(device.getIp(), info))
                                .toList()))
                .toList();
    }

    private PortInfo enrichPort(String ip, PortInfo info) {
        String banner = bannerGrabber.grab(ip, info.port());
        String service = serviceIdentifier.identify(info.port(), banner).orElse(null);
        return new PortInfo(info.port(), service, banner);
    }

    static String describe(Device device) {
        StringBuilder sb = new StringBuilder();
        sb.append(device.getHostname() != null ? "[" + device.getHostname() + "] " : "");
        sb.append(device.getVendor() != null ? device.getVendor() + " " : "");
        sb.append("(").append(device.getMac()).append(") - ").append(device.getIp());
        if (device.getOsGuess() != null) {
            sb.append(" [").append(device.getOsGuess()).append("]");
        }
        sb.append("\n");
        if (device.getPortInfos().isEmpty()) {
            sb.append("  nenhuma porta comum aberta");
        } else {
            for (PortInfo info : device.getPortInfos()) {
                sb.append("  • ").append(info.port()).append("/tcp");
                if (info.service() != null) {
                    sb.append("   ").append(info.service());
                }
                sb.append("\n");
            }
        }
        return sb.toString().trim();
    }

    public static PortScanStrategy createStrategy(String mode) {
        int timeout = AppConfig.getInt("scanner.timeout", 150);
        var socketFactory = new RealSocketFactory(timeout);

        return switch (mode) {
            case "--quick" -> new QuickScanStrategy(socketFactory);
            case "--full" -> new FullScanStrategy(socketFactory);
            case "--stable" -> new StableScanStrategy(socketFactory);
            default -> null;
        };
    }
}
