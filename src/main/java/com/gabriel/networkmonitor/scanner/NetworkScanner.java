package com.gabriel.networkmonitor.scanner;

import com.gabriel.networkmonitor.interfaces.PortScanStrategy;
import com.gabriel.networkmonitor.model.Device;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class NetworkScanner {

    private static final Logger logger = LoggerFactory.getLogger(NetworkScanner.class);

    private final PortScanStrategy portScanner;

    public NetworkScanner(PortScanStrategy strategy) {
        this.portScanner = strategy;
    }

    public List<Device> fullScan(List<String> activeIps) throws InterruptedException {
        logger.info("Verificando portas de cada dispositivo...");
        List<Device> devices = new ArrayList<>();

        for (String ip : activeIps) {
            List<Integer> ports = portScanner.scanIp(ip);
            devices.add(new Device(ip, ports));
        }

        return devices;
    }

}
