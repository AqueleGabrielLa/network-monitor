package com.gabriel.networkmonitor;

import com.gabriel.networkmonitor.config.AppConfig;
import com.gabriel.networkmonitor.interfaces.PortScanStrategy;
import com.gabriel.networkmonitor.network.SubnetDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws InterruptedException {
        String subnet = null;
        String mode = "--quick";

        for (String arg : args) {
            if (arg.startsWith("--")) {
                mode = arg;
            } else if (subnet == null) {
                subnet = arg;
            }
        }

        PortScanStrategy strategy = ScannerService.createStrategy(mode);
        if (strategy == null) {
            System.out.println("Modo desconhecido: " + mode + ". Use --quick, --stable ou --full.");
            return;
        }

        subnet = resolveSubnet(subnet);
        if (subnet == null) {
            System.out.println("Nao foi possivel determinar a subnet da rede. "
                    + "Informe manualmente como argumento (ex: 192.168.0).");
            return;
        }

        logger.info("Subnet alvo: {}", subnet);

        ScannerService service = new ScannerService(strategy);
        service.executeFullCycle(subnet);
    }

    static String resolveSubnet(String provided) {
        if (provided != null && !provided.isBlank()) {
            return provided;
        }

        String autoDetected = new SubnetDetector().detect();
        if (autoDetected != null) {
            return autoDetected;
        }

        String configured = AppConfig.getString("scanner.subnet", null);
        return configured != null && !configured.isBlank() ? configured : null;
    }
}