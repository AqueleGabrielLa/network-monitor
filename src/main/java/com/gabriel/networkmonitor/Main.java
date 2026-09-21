package com.gabriel.networkmonitor;

import com.gabriel.networkmonitor.interfaces.PortScanStrategy;
import com.gabriel.networkmonitor.model.Device;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws InterruptedException {
        if (args.length < 1) {
            System.out.println("Uso: java -jar network-monitor.jar <subnet> [--quick|--stable|--full]");
            System.out.println("Exemplo: java -jar network-monitor.jar 192.168.0 --quick");
            return;
        }

        String subnet = args[0];
        String mode = args.length > 1 ? args[1] : "--quick";

        PortScanStrategy strategy = ScannerService.createStrategy(mode);
        if (strategy == null) {
            System.out.println("Modo desconhecido: " + mode + ". Use --quick, --stable ou --full.");
            return;
        }

        ScannerService service = new ScannerService(strategy);
        service.executeFullCycle(subnet);
    }
}
