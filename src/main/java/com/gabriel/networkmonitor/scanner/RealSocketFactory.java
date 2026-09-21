package com.gabriel.networkmonitor.scanner;

import com.gabriel.networkmonitor.interfaces.SocketFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public class RealSocketFactory implements SocketFactory {

    private final int timeout;

    public RealSocketFactory(int timeout) {
        this.timeout = timeout;
    }

    @Override
    public Socket create(String host, int port) throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeout);
        return socket;
    }
}
