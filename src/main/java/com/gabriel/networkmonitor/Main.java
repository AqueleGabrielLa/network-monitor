package com.gabriel.networkmonitor;

import com.gabriel.networkmonitor.config.AppConfig;
import com.gabriel.networkmonitor.interfaces.PortScanStrategy;
import com.gabriel.networkmonitor.network.SubnetDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws InterruptedException {
        String mode = args.length > 1 ? args[1] : "--quick";

        PortScanStrategy strategy = ScannerService.createStrategy(mode);
        if (strategy == null) {
            System.out.println("Modo desconhecido: " + mode + ". Use --quick, --stable ou --full.");
            return;
        }

        String subnet = resolveSubnet(args);
        if (subnet == null) {
            System.out.println("Nao foi possivel determinar a subnet da rede. "
                    + "Informe manualmente como argumento (ex: 192.168.0).");
            return;
        }

        logger.info("Subnet alvo: {}", subnet);

        ScannerService service = new ScannerService(strategy);
        service.executeFullCycle(subnet);
    }

    static String resolveSubnet(String[] args) {
        if (args.length >= 1) {
            return args[0];
        }

        String autoDetected = new SubnetDetector().detect();
        if (autoDetected != null) {
            return autoDetected;
        }

        return AppConfig.getString("scanner.subnet", null);
    }
}