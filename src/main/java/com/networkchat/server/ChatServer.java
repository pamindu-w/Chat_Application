package com.networkchat.server;

import com.networkchat.network.NetworkConfig;
import java.nio.channels.ServerSocketChannel;

/**
 * ChatServer - Member 1 responsibility: Server Setup, TCP/IP, Addressing and Ports.
 *
 * Baseline class structure and configuration fields.
 */
public class ChatServer {

    private final String bindAddress;
    private final int port;
    private ServerSocketChannel serverChannel;
    private volatile boolean running = false;

    public ChatServer() {
        this(NetworkConfig.DEFAULT_BIND_ADDRESS, NetworkConfig.DEFAULT_PORT);
    }

    public ChatServer(int port) {
        this(NetworkConfig.DEFAULT_BIND_ADDRESS, port);
    }

    public ChatServer(String bindAddress, int port) {
        this.bindAddress = (bindAddress == null || bindAddress.isBlank())
                ? NetworkConfig.DEFAULT_BIND_ADDRESS
                : bindAddress.trim();
        this.port = port;
    }

    public String bindAddress() {
        return bindAddress;
    }

    public int port() {
        return port;
    }

    public boolean isRunning() {
        return running;
    }

    public ServerSocketChannel serverChannel() {
        return serverChannel;
    }
}
