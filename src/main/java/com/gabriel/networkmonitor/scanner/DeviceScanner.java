package com.gabriel.networkmonitor.scanner;

import com.gabriel.networkmonitor.model.ArpEntry;
import com.gabriel.networkmonitor.model.Device;
import com.gabriel.networkmonitor.network.OuiLookup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

public class DeviceScanner {

    private static final Logger logger = LoggerFactory.getLogger(DeviceScanner.class);

    private final ArpScanner arpScanner;
    private final OuiLookup ouiLookup;

    public DeviceScanner() {
        this.arpScanner = new ArpScanner();
        this.ouiLookup = new OuiLookup();
    }

    public List<Device> scanRange(String subnet) {
        List<Device> devices = new ArrayList<>();

        List<ArpEntry> entries;
        try {
            entries = arpScanner.scan();
        } catch (Exception e) {
            logger.error("Falha ao ler o cache ARP: {}", e.getMessage());
            return devices;
        }

        for (ArpEntry entry : entries) {
            String ip = entry.ip();
            if (subnet != null && !subnet.isBlank() && !ip.startsWith(subnet + ".")) {
                continue;
            }

            String hostname = resolveHostname(ip);
            String vendor = ouiLookup.lookup(entry.mac());

            devices.add(new Device(entry.mac(), ip, hostname, vendor, List.of()));
            logger.info("Dispositivo ativo encontrado: {} [{}]{}", ip, entry.mac(),
                    vendor != null ? " - " + vendor : "");
        }

        return devices;
    }

    private String resolveHostname(String ip) {
        try {
            String hostname = InetAddress.getByName(ip).getHostName();
            if (hostname == null || hostname.isBlank() || hostname.equals(ip)) {
                return null;
            }
            return hostname;
        } catch (Exception e) {
            logger.debug("Falha ao resolver hostname de {}: {}", ip, e.getMessage());
            return null;
        }
    }

}