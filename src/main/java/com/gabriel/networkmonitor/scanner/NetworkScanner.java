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

    public List<Device> fullScan(List<Device> activeDevices) throws InterruptedException {
        logger.info("Verificando portas de cada dispositivo...");
        List<Device> devices = new ArrayList<>();

        for (Device device : activeDevices) {
            List<Integer> ports = portScanner.scanIp(device.getIp());
            devices.add(device.withPorts(ports));
        }

        return devices;
    }

}
