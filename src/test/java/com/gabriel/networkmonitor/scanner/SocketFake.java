package com.gabriel.networkmonitor.scanner;

import com.gabriel.networkmonitor.interfaces.SocketFactory;

import java.io.IOException;
import java.net.Socket;
import java.util.Set;

public class SocketFake implements SocketFactory {

    private final Set<Integer> openPorts;

    public SocketFake(Set<Integer> openPorts) {
        this.openPorts = openPorts;
    }

    @Override
    public Socket create(String host, int port) throws IOException {
        if (openPorts.contains(port)) {
            return new Socket();
        }
        throw new IOException("Connection refused");
    }
}
