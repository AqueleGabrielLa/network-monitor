package com.gabriel.networkmonitor.interfaces;

import java.io.IOException;
import java.net.Socket;

public interface SocketFactory {
    Socket create(String host, int port) throws IOException;
}
