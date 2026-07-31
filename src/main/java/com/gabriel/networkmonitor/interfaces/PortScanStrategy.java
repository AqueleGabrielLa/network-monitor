package com.gabriel.networkmonitor.interfaces;

import java.util.List;

public interface PortScanStrategy {

    public List<Integer> scanIp(String ip) throws InterruptedException;

}
